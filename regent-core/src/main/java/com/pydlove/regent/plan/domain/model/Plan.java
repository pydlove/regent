package com.pydlove.regent.plan.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 计划聚合。
 *
 * <p>计划只保存任务 ID，执行时由应用服务显式读取任务并交给任务派发服务；
 * 它不持有 Task，也不直接修改任务状态。
 */
public class Plan {

    public static final int MAX_TASKS = 200;

    private PlanId id;
    private final UserId ownerId;
    private final String name;
    private final List<TaskId> taskIds;
    private PlanStatus status;
    private final Instant scheduledAt;
    private Instant startedAt;
    private Instant finishedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Plan(UserId ownerId,
                 String name,
                 List<TaskId> taskIds,
                 PlanStatus status,
                 Instant scheduledAt,
                 Instant startedAt,
                 Instant finishedAt,
                 Instant createdAt) {
        this.ownerId = ownerId;
        this.name = requireName(name);
        this.taskIds = requireTasks(taskIds);
        this.status = requireNotNull(status, "计划状态");
        this.scheduledAt = requireNotNull(scheduledAt, "计划执行时间");
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.createdAt = requireNotNull(createdAt, "创建时间");
        this.updatedAt = createdAt;
    }

    public static Plan schedule(String name, List<TaskId> taskIds, Instant scheduledAt) {
        return schedule(name, taskIds, scheduledAt, null);
    }

    /** 创建计划并记录归属账号；接口侧必须使用这一条。 */
    public static Plan schedule(String name, List<TaskId> taskIds, Instant scheduledAt, UserId ownerId) {
        Instant now = now();
        return new Plan(ownerId, name, taskIds, PlanStatus.SCHEDULED,
                requireNotNull(scheduledAt, "计划执行时间"), null, null, now);
    }

    public static Plan restore(PlanSnapshot snapshot) {
        Plan plan = new Plan(
                snapshot.ownerId(),
                snapshot.name(),
                snapshot.taskIds(),
                snapshot.status(),
                snapshot.scheduledAt(),
                snapshot.startedAt(),
                snapshot.finishedAt(),
                snapshot.createdAt());
        plan.id = requireNotNull(snapshot.id(), "计划 ID");
        plan.updatedAt = requireNotNull(snapshot.updatedAt(), "更新时间");
        return plan;
    }

    public void assignIdentity(PlanId assigned) {
        if (id != null) {
            throw new DomainException("计划已有 ID，不能重复分配：" + id);
        }
        id = requireNotNull(assigned, "计划 ID");
    }

    public boolean isDueAt(Instant now) {
        return status == PlanStatus.SCHEDULED && !scheduledAt.isAfter(requireNotNull(now, "当前时间"));
    }

    /**
     * 开始本轮执行。人工触发允许提前运行，定时触发必须已经到点；
     * 两者都会把计划锁进 RUNNING，防止同一个计划被重复拉起。
     */
    public void start(Instant now, PlanTrigger trigger) {
        Instant current = requireNotNull(now, "当前时间");
        requireNotNull(trigger, "触发方式");
        requireStatus(PlanStatus.SCHEDULED, "只有待执行计划可以开始");
        if (trigger == PlanTrigger.SCHEDULED && scheduledAt.isAfter(current)) {
            throw new DomainException("计划尚未到执行时间：" + scheduledAt);
        }
        status = PlanStatus.RUNNING;
        startedAt = current;
        finishedAt = null;
        updatedAt = current;
    }

    public void complete(Instant now) {
        Instant current = requireNotNull(now, "完成时间");
        requireStatus(PlanStatus.RUNNING, "只有执行中的计划可以完成");
        status = PlanStatus.COMPLETED;
        finishedAt = current;
        updatedAt = current;
    }

    private void requireStatus(PlanStatus expected, String message) {
        if (status != expected) {
            throw new DomainException(message + "，当前状态：" + status);
        }
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("计划名称不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 128) {
            throw new DomainException("计划名称超长（上限 128 字符）：" + trimmed.length());
        }
        return trimmed;
    }

    private static List<TaskId> requireTasks(List<TaskId> values) {
        if (values == null || values.isEmpty()) {
            throw new DomainException("计划至少需要纳管一个任务");
        }
        LinkedHashSet<TaskId> unique = new LinkedHashSet<>(values);
        if (unique.contains(null)) {
            throw new DomainException("计划中的任务 ID 不能为空");
        }
        if (unique.size() > MAX_TASKS) {
            throw new DomainException("一个计划最多纳管 " + MAX_TASKS + " 个任务");
        }
        return List.copyOf(unique);
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public PlanId getId() {
        return id;
    }

    public UserId getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public List<TaskId> getTaskIds() {
        return taskIds;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
