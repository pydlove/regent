package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.DailyCompletionCount;
import com.pydlove.regent.execution.domain.repository.DailyTokenUsage;
import com.pydlove.regent.execution.domain.repository.HourlyTokenUsage;
import com.pydlove.regent.execution.domain.repository.TaskListItemPage;
import com.pydlove.regent.execution.domain.repository.TaskQuery;
import com.pydlove.regent.execution.domain.repository.TaskQueryRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务读侧用例。刻意只做编排、不做判断：筛选与分页下推给仓储的 SQL，
 * 聚合内部的状态判断留在领域方法里。
 */
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final TaskQueryRepository taskQueryRepository;
    private final ZoneId zone;

    /**
     * {@code zone} 决定「一天」从几点切开。工作台上的柱状图与列表里的「几分钟前」必须是同一套
     * 时间口径，所以时区由装配方显式给进来，不在服务里读系统默认值。
     */
    public TaskQueryService(TaskRepository taskRepository,
                            TaskQueryRepository taskQueryRepository,
                            ZoneId zone) {
        this.taskRepository = taskRepository;
        this.taskQueryRepository = taskQueryRepository;
        this.zone = zone;
    }

    public TaskListItemPage list(TaskQuery query) {
        return taskQueryRepository.findPage(query);
    }

    /** 详情取聚合：状态守卫、可执行动作都以聚合为准，不另开一份读模型。 */
    public Task get(TaskId id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + id));
    }

    /** 只读取属于当前账号的任务；不存在与无权访问使用同一条提示。 */
    public Task get(TaskId id, UserId ownerId) {
        return taskRepository.findByIdOwned(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + id));
    }

    /** 状态分布。缺失的状态补 0，让调用方拿到的键集合始终覆盖全部状态。 */
    public Map<TaskStatus, Long> statusCounts(ProjectId projectId) {
        return statusCounts(projectId, null);
    }

    public Map<TaskStatus, Long> statusCounts(ProjectId projectId, UserId ownerId) {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        counts.putAll(taskQueryRepository.countGroupByStatus(projectId, ownerId));
        return counts;
    }

    /** 工作台用的 token 汇总，只统计未逻辑删除任务下的执行记录。 */
    public TokenUsage tokenUsage(ProjectId projectId) {
        return tokenUsage(projectId, null);
    }

    public TokenUsage tokenUsage(ProjectId projectId, UserId ownerId) {
        return taskQueryRepository.summarizeTokens(projectId, ownerId);
    }

    /**
     * 按日期查看 Token 消耗。除“全部”外都补齐范围内的空白日期，
     * 返回的日期始终以用户时区解释。
     */
    public List<DailyTokenUsage> tokenUsageByDay(ProjectId projectId, TokenUsageRange range) {
        return tokenUsageByDay(projectId, null, range);
    }

    public List<DailyTokenUsage> tokenUsageByDay(ProjectId projectId, UserId ownerId,
                                                 TokenUsageRange range) {
        TokenUsageRange effectiveRange = range == null ? TokenUsageRange.LAST_7_DAYS : range;
        Instant now = Instant.now();
        ZoneOffset offset = zone.getRules().getOffset(now);
        LocalDate today = LocalDate.now(zone);
        Instant from = switch (effectiveRange) {
            case TODAY -> today.atStartOfDay(zone).toInstant();
            case LAST_24_HOURS -> now.minus(24, ChronoUnit.HOURS);
            case LAST_7_DAYS -> today.minusDays(6).atStartOfDay(zone).toInstant();
            case LAST_14_DAYS -> today.minusDays(13).atStartOfDay(zone).toInstant();
            case LAST_30_DAYS -> today.minusDays(29).atStartOfDay(zone).toInstant();
            case ALL -> null;
        };

        Map<LocalDate, DailyTokenUsage> counted = new HashMap<>();
        for (DailyTokenUsage usage : taskQueryRepository.summarizeTokensPerDay(
                projectId, ownerId, from, now, offset)) {
            counted.put(usage.day(), usage);
        }
        if (effectiveRange == TokenUsageRange.ALL) {
            return counted.values().stream()
                    .sorted(java.util.Comparator.comparing(DailyTokenUsage::day))
                    .toList();
        }

        LocalDate fromDay = from.atZone(zone).toLocalDate();
        List<DailyTokenUsage> series = new ArrayList<>();
        for (LocalDate day = fromDay; !day.isAfter(today); day = day.plusDays(1)) {
            series.add(counted.getOrDefault(day,
                    new DailyTokenUsage(day, TokenUsage.ZERO, 0L)));
        }
        return series;
    }

    /** 当天与滚动 24 小时的 Token 趋势，按整点小时补齐。 */
    public List<HourlyTokenUsage> tokenUsageByHour(ProjectId projectId, TokenUsageRange range) {
        return tokenUsageByHour(projectId, null, range);
    }

    public List<HourlyTokenUsage> tokenUsageByHour(ProjectId projectId, UserId ownerId,
                                                   TokenUsageRange range) {
        TokenUsageRange effectiveRange = range == null ? TokenUsageRange.TODAY : range;
        if (effectiveRange != TokenUsageRange.TODAY
                && effectiveRange != TokenUsageRange.LAST_24_HOURS) {
            throw new IllegalArgumentException("小时趋势只支持当天或近 24 小时");
        }
        Instant now = Instant.now();
        ZoneOffset offset = zone.getRules().getOffset(now);
        LocalDate today = LocalDate.now(zone);
        Instant from = effectiveRange == TokenUsageRange.TODAY
                ? today.atStartOfDay(zone).toInstant()
                : now.minus(24, ChronoUnit.HOURS);

        Map<LocalDateTime, HourlyTokenUsage> counted = new HashMap<>();
        for (HourlyTokenUsage usage : taskQueryRepository.summarizeTokensPerHour(
                projectId, ownerId, from, now, offset)) {
            counted.put(usage.bucketStart(), usage);
        }

        LocalDateTime firstHour = from.atZone(zone)
                .withMinute(0).withSecond(0).withNano(0).toLocalDateTime();
        LocalDateTime currentHour = now.atZone(zone)
                .withMinute(0).withSecond(0).withNano(0).toLocalDateTime();
        List<HourlyTokenUsage> series = new ArrayList<>();
        for (LocalDateTime hour = firstHour;
             !hour.isAfter(currentHour);
             hour = hour.plusHours(1)) {
            series.add(counted.getOrDefault(hour,
                    new HourlyTokenUsage(hour, TokenUsage.ZERO, 0L)));
        }
        return series;
    }

    /**
     * 近 {@code days} 天的收场趋势，含今天，逐日补齐。
     *
     * <p>补零放在应用层：调用方拿到的长度恒等于天数、日期连续，画图不必再处理空档。
     * 切天偏移取「此刻」的偏移量；本项目按中国时区使用（无夏令时），不做跨时区切换的补偿。
     */
    public List<DailyCompletionCount> completionTrend(ProjectId projectId, int days) {
        return completionTrend(projectId, null, days);
    }

    public List<DailyCompletionCount> completionTrend(ProjectId projectId, UserId ownerId, int days) {
        if (days < 1 || days > 30) {
            throw new IllegalArgumentException("趋势天数必须在 1 到 30 之间");
        }
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, DailyCompletionCount> counted = new HashMap<>();
        for (DailyCompletionCount count : taskQueryRepository
                .countCompletionPerDay(projectId, ownerId, from, today,
                        zone.getRules().getOffset(Instant.now()))) {
            counted.put(count.day(), count);
        }
        List<DailyCompletionCount> series = new ArrayList<>(days);
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            series.add(counted.getOrDefault(day, new DailyCompletionCount(day, 0L, 0L)));
        }
        return series;
    }
}
