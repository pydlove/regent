package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.TaskSource;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.DailyCompletionCount;
import com.pydlove.regent.execution.domain.repository.DailyTokenUsage;
import com.pydlove.regent.execution.domain.repository.HourlyTokenUsage;
import com.pydlove.regent.execution.domain.repository.TaskListItem;
import com.pydlove.regent.execution.domain.repository.TaskListItemPage;
import com.pydlove.regent.execution.domain.repository.TaskQuery;
import com.pydlove.regent.execution.domain.repository.TaskQueryRepository;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.shared.kernel.TokenUsage;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 任务查询仓储的 MyBatis 实现。 */
@Repository
public class MyBatisTaskQueryRepository implements TaskQueryRepository {

    private final TaskMapper taskMapper;

    public MyBatisTaskQueryRepository(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public TaskListItemPage findPage(TaskQuery query) {
        String status = query.status() == null ? null : query.status().name();
        String level = query.level() == null ? null : query.level().name();
        Long projectId = query.projectId() == null ? null : query.projectId().value();
        Long taskId = query.taskId() == null ? null : query.taskId().value();
        Long ownerId = query.ownerId() == null ? null : query.ownerId().value();
        String keyword = escapeLike(query.keyword());
        long total = taskMapper.countListBy(status, level, projectId, taskId, keyword, ownerId);
        if (total == 0) {
            return new TaskListItemPage(List.of(), 0, query.page(), query.size());
        }
        List<TaskListItem> items = taskMapper
                .selectListPage(status, level, projectId, taskId, keyword, ownerId,
                        query.size(), query.offset())
                .stream()
                .map(MyBatisTaskQueryRepository::toItem)
                .toList();
        return new TaskListItemPage(items, total, query.page(), query.size());
    }

    @Override
    public Map<TaskStatus, Long> countGroupByStatus(ProjectId projectId) {
        return countGroupByStatus(projectId, null);
    }

    @Override
    public Map<TaskStatus, Long> countGroupByStatus(ProjectId projectId, UserId ownerId) {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (Map<String, Object> row : taskMapper.countGroupByStatus(
                projectIdValue(projectId), ownerIdValue(ownerId))) {
            counts.put(TaskStatus.valueOf((String) row.get("status")),
                    ((Number) row.get("cnt")).longValue());
        }
        return counts;
    }

    @Override
    public TokenUsage summarizeTokens(ProjectId projectId) {
        return summarizeTokens(projectId, null);
    }

    @Override
    public TokenUsage summarizeTokens(ProjectId projectId, UserId ownerId) {
        TokenUsageRow row = taskMapper.summarizeTokens(projectIdValue(projectId), ownerIdValue(ownerId));
        if (row == null) {
            return TokenUsage.ZERO;
        }
        return TokenUsage.of(
                nullToZero(row.getTokenInput()),
                nullToZero(row.getTokenCachedInput()),
                nullToZero(row.getTokenOutput()));
    }

    @Override
    public List<DailyTokenUsage> summarizeTokensPerDay(ProjectId projectId,
                                                       Instant from,
                                                       Instant to,
                                                       ZoneOffset offset) {
        return summarizeTokensPerDay(projectId, null, from, to, offset);
    }

    @Override
    public List<DailyTokenUsage> summarizeTokensPerDay(ProjectId projectId,
                                                       UserId ownerId,
                                                       Instant from,
                                                       Instant to,
                                                       ZoneOffset offset) {
        return taskMapper.summarizeTokensPerDay(
                        projectIdValue(projectId),
                        ownerIdValue(ownerId),
                        from == null ? null : LocalDateTime.ofInstant(from, ZoneOffset.UTC),
                        to == null ? null : LocalDateTime.ofInstant(to, ZoneOffset.UTC),
                        offset.getTotalSeconds() / 60).stream()
                .map(row -> new DailyTokenUsage(
                        row.getDay(),
                        TokenUsage.of(
                                nullToZero(row.getTokenInput()),
                                nullToZero(row.getTokenCachedInput()),
                                nullToZero(row.getTokenOutput())),
                        nullToZero(row.getRunCount())))
                .toList();
    }

    @Override
    public List<HourlyTokenUsage> summarizeTokensPerHour(ProjectId projectId,
                                                         Instant from,
                                                         Instant to,
                                                         ZoneOffset offset) {
        return summarizeTokensPerHour(projectId, null, from, to, offset);
    }

    @Override
    public List<HourlyTokenUsage> summarizeTokensPerHour(ProjectId projectId,
                                                         UserId ownerId,
                                                         Instant from,
                                                         Instant to,
                                                         ZoneOffset offset) {
        return taskMapper.summarizeTokensPerHour(
                        projectIdValue(projectId),
                        ownerIdValue(ownerId),
                        LocalDateTime.ofInstant(from, ZoneOffset.UTC),
                        LocalDateTime.ofInstant(to, ZoneOffset.UTC),
                        offset.getTotalSeconds() / 60).stream()
                .map(row -> new HourlyTokenUsage(
                        row.getBucketStart(),
                        TokenUsage.of(
                                nullToZero(row.getTokenInput()),
                                nullToZero(row.getTokenCachedInput()),
                                nullToZero(row.getTokenOutput())),
                        nullToZero(row.getRunCount())))
                .toList();
    }

    @Override
    public List<DailyCompletionCount> countCompletionPerDay(ProjectId projectId,
                                                            LocalDate from,
                                                            LocalDate to,
                                                            ZoneOffset offset) {
        return countCompletionPerDay(projectId, null, from, to, offset);
    }

    @Override
    public List<DailyCompletionCount> countCompletionPerDay(ProjectId projectId,
                                                            UserId ownerId,
                                                            LocalDate from,
                                                            LocalDate to,
                                                            ZoneOffset offset) {
        // 领域侧给的是「用户时区的自然日」，仓储负责换算成 UTC 字面量：库里存的始终是 UTC
        LocalDateTime fromUtc = from.atStartOfDay().toInstant(offset).atZone(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime toUtc = to.plusDays(1).atStartOfDay().toInstant(offset).atZone(ZoneOffset.UTC).toLocalDateTime();
        return taskMapper.countCompletionPerDay(
                        projectIdValue(projectId), ownerIdValue(ownerId),
                        fromUtc, toUtc, offset.getTotalSeconds() / 60).stream()
                .map(row -> new DailyCompletionCount(row.getDay(), row.getDone(), row.getFailed()))
                .toList();
    }

    private static TaskListItem toItem(TaskListRow row) {
        return new TaskListItem(
                row.getId(),
                row.getProjectId(),
                row.getProjectName(),
                TaskSource.valueOf(row.getSource()),
                row.getTitle(),
                TaskStatus.valueOf(row.getStatus()),
                row.getPriority(),
                row.getLevel() == null ? DecisionLevel.L0 : DecisionLevel.parse(row.getLevel()),
                row.getBranchName(),
                row.getBlockingDecisionId() == null ? null : DecisionId.of(row.getBlockingDecisionId()),
                row.getAttemptCount(),
                row.getTokens() == null ? 0L : row.getTokens(),
                row.getExecutionMillis() == null ? 0L : row.getExecutionMillis(),
                fromDb(row.getStartedAt()),
                fromDb(row.getFinishedAt()),
                fromDb(row.getCreatedAt()),
                fromDb(row.getUpdatedAt()));
    }

    private static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private static Long projectIdValue(ProjectId projectId) {
        return projectId == null ? null : projectId.value();
    }

    private static Long ownerIdValue(UserId ownerId) {
        return ownerId == null ? null : ownerId.value();
    }

    /** 转义 LIKE 通配符，让用户输入的 % 只匹配它自己（参数是绑定的，这里防的是语义意外，不是注入）。 */
    private static String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
