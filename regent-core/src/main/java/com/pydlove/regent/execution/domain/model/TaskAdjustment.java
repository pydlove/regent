package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 执行期间新增的调整方向。
 *
 * <p>它不是任务指令的替代品：原任务指令保持不变，调整记录单独留痕，并明确标出是来自
 * 另一条任务还是临时补充。来源任务只保存 ID 与内容快照，不持有任务对象；来源任务之后被编辑
 * 或删除也不会改变当时下发的方向。
 */
public class TaskAdjustment {

    public static final int MAX_CONTENT_LENGTH = 12_000;

    private TaskAdjustmentId id;
    private final TaskId taskId;
    private final TaskAdjustmentSource source;
    private final TaskId sourceTaskId;
    private final String sourceTitle;
    private final String content;
    private TaskAdjustmentStatus status;
    private TaskRunId appliedRunId;
    private final Instant createdAt;
    private Instant appliedAt;

    private TaskAdjustment(TaskId taskId,
                           TaskAdjustmentSource source,
                           TaskId sourceTaskId,
                           String sourceTitle,
                           String content,
                           TaskAdjustmentStatus status,
                           TaskRunId appliedRunId,
                           Instant createdAt,
                           Instant appliedAt) {
        this.taskId = requireNotNull(taskId, "目标任务");
        this.source = requireNotNull(source, "调整来源");
        this.content = requireContent(content);
        this.sourceTitle = blankToNull(sourceTitle);
        this.status = requireNotNull(status, "调整状态");
        this.appliedRunId = appliedRunId;
        this.createdAt = requireNotNull(createdAt, "创建时间");
        this.appliedAt = appliedAt;
        if (source == TaskAdjustmentSource.TASK) {
            this.sourceTaskId = requireNotNull(sourceTaskId, "来源任务");
            if (this.sourceTaskId.equals(taskId)) {
                throw new DomainException("来源任务不能是目标任务本身");
            }
        } else {
            if (sourceTaskId != null || this.sourceTitle != null) {
                throw new DomainException("临时调整方向不能携带来源任务信息");
            }
            this.sourceTaskId = null;
        }
        if (status == TaskAdjustmentStatus.APPLIED && (appliedRunId == null || appliedAt == null)) {
            throw new DomainException("已下发的调整方向必须记录执行记录与下发时间");
        }
        if (status == TaskAdjustmentStatus.PENDING && (appliedRunId != null || appliedAt != null)) {
            throw new DomainException("待下发调整方向不能提前记录下发结果");
        }
    }

    public static TaskAdjustment fromTask(TaskId taskId, TaskId sourceTaskId,
                                          String sourceTitle, String content) {
        Instant now = now();
        return new TaskAdjustment(taskId, TaskAdjustmentSource.TASK, sourceTaskId,
                sourceTitle, content, TaskAdjustmentStatus.PENDING, null, now, null);
    }

    public static TaskAdjustment temporary(TaskId taskId, String content) {
        Instant now = now();
        return new TaskAdjustment(taskId, TaskAdjustmentSource.TEMPORARY, null,
                null, content, TaskAdjustmentStatus.PENDING, null, now, null);
    }

    public static TaskAdjustment restore(TaskAdjustmentId id,
                                         TaskId taskId,
                                         TaskAdjustmentSource source,
                                         TaskId sourceTaskId,
                                         String sourceTitle,
                                         String content,
                                         TaskAdjustmentStatus status,
                                         TaskRunId appliedRunId,
                                         Instant createdAt,
                                         Instant appliedAt) {
        TaskAdjustment adjustment = new TaskAdjustment(taskId, source, sourceTaskId, sourceTitle,
                content, status, appliedRunId, createdAt, appliedAt);
        adjustment.id = requireNotNull(id, "调整方向 ID");
        return adjustment;
    }

    /** 标记已经随某次尝试交给执行器；重复标记直接报错，避免审计时间被后写覆盖。 */
    public void markApplied(TaskRunId runId) {
        if (status != TaskAdjustmentStatus.PENDING) {
            throw new DomainException("调整方向已经下发，不能重复标记：" + id);
        }
        this.status = TaskAdjustmentStatus.APPLIED;
        this.appliedRunId = requireNotNull(runId, "下发执行记录");
        this.appliedAt = now();
    }

    public void assignIdentity(TaskAdjustmentId assigned) {
        if (id != null) {
            throw new DomainException("调整方向已有 ID，不能重复分配：" + id);
        }
        this.id = requireNotNull(assigned, "调整方向 ID");
    }

    public TaskAdjustmentId getId() {
        return id;
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public TaskAdjustmentSource getSource() {
        return source;
    }

    public Optional<TaskId> getSourceTaskId() {
        return Optional.ofNullable(sourceTaskId);
    }

    public Optional<String> getSourceTitle() {
        return Optional.ofNullable(sourceTitle);
    }

    public String getContent() {
        return content;
    }

    public TaskAdjustmentStatus getStatus() {
        return status;
    }

    public Optional<TaskRunId> getAppliedRunId() {
        return Optional.ofNullable(appliedRunId);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Optional<Instant> getAppliedAt() {
        return Optional.ofNullable(appliedAt);
    }

    private static String requireContent(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("调整方向不能为空");
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new DomainException("调整方向超长（上限 " + MAX_CONTENT_LENGTH + " 字符）："
                    + normalized.length());
        }
        return normalized;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
