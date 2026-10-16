package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskSnapshot;
import com.pydlove.regent.execution.domain.model.TaskSource;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * 领域对象 ↔ 数据对象的转换。唯一知道「表里那一串 VARCHAR 和这个枚举是一回事」的地方。
 *
 * <p>它刻意是静态工具而不是 Spring Bean：转换没有依赖、没有状态，做成 Bean 只会
 * 逼着每个仓储实现都注一次构。
 */
public final class TaskDataMapper {

    private TaskDataMapper() {
    }

    public static TaskData toData(Task task) {
        TaskData data = new TaskData();
        data.setId(task.getId() == null ? null : task.getId().value());
        data.setProjectId(task.getProjectId().value());
        data.setRequirementId(task.getRequirementId().map(RequirementId::value).orElse(null));
        data.setBugId(task.getBugId().map(BugId::value).orElse(null));
        data.setSource(task.getSource().name());
        data.setTitle(task.getTitle());
        data.setPrompt(task.getPrompt());
        data.setStatus(task.getStatus().name());
        data.setPriority(task.getPriority());
        data.setCodexThreadId(task.getCodexThreadId().orElse(null));
        data.setBranchName(task.getBranchName().orElse(null));
        data.setBlockingDecisionId(task.getBlockingDecisionId().map(DecisionId::value).orElse(null));
        data.setAttemptCount(task.getAttemptCount());
        data.setStartedAt(toDb(task.getStartedAt().orElse(null)));
        data.setFinishedAt(toDb(task.getFinishedAt().orElse(null)));
        data.setCreatedAt(toDb(task.getCreatedAt()));
        data.setUpdatedAt(toDb(task.getUpdatedAt()));
        return data;
    }

    public static Task toDomain(TaskData data) {
        return Task.restore(new TaskSnapshot(
                TaskId.of(data.getId()),
                ProjectId.of(data.getProjectId()),
                optionalId(data.getRequirementId()).map(RequirementId::of).orElse(null),
                optionalId(data.getBugId()).map(BugId::of).orElse(null),
                TaskSource.valueOf(data.getSource()),
                data.getTitle(),
                data.getPrompt(),
                data.getPriority(),
                TaskStatus.valueOf(data.getStatus()),
                data.getCodexThreadId(),
                data.getBranchName(),
                optionalId(data.getBlockingDecisionId()).map(DecisionId::of).orElse(null),
                data.getAttemptCount(),
                fromDb(data.getStartedAt()),
                fromDb(data.getFinishedAt()),
                fromDb(data.getCreatedAt()),
                fromDb(data.getUpdatedAt())));
    }

    private static Optional<Long> optionalId(Long value) {
        return Optional.ofNullable(value);
    }

    /** UTC 瞬时 → DATETIME 字面量。DATETIME 里没有时区，约定它存的一律是 UTC。 */
    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** DATETIME 字面量 → UTC 瞬时。读回来的值按 UTC 解释，与本机时区无关。 */
    private static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
