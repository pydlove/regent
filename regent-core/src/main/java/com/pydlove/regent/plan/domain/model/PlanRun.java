package com.pydlove.regent.plan.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次计划执行。它冻结任务标题、逐项记录派发和最终结果，并在所有任务收场后生成总结。
 */
public class PlanRun {

    private PlanRunId id;
    private final PlanId planId;
    private final String planName;
    private final PlanTrigger trigger;
    private PlanRunStatus status;
    private final List<PlanRunItem> items;
    private String summary;
    private final Instant startedAt;
    private Instant finishedAt;
    private final Instant createdAt;

    private PlanRun(PlanId planId,
                    String planName,
                    PlanTrigger trigger,
                    PlanRunStatus status,
                    List<PlanRunItem> items,
                    String summary,
                    Instant startedAt,
                    Instant finishedAt,
                    Instant createdAt) {
        this.planId = requireNotNull(planId, "计划 ID");
        this.planName = requireNotNull(planName, "计划名称");
        this.trigger = requireNotNull(trigger, "触发方式");
        this.status = requireNotNull(status, "执行状态");
        this.items = new ArrayList<>(items);
        if (this.items.isEmpty()) {
            throw new DomainException("计划执行至少需要一个任务");
        }
        this.summary = summary;
        this.startedAt = requireNotNull(startedAt, "开始时间");
        this.finishedAt = finishedAt;
        this.createdAt = requireNotNull(createdAt, "创建时间");
    }

    public static PlanRun start(PlanId planId,
                                String planName,
                                PlanTrigger trigger,
                                List<PlanTaskSnapshot> tasks) {
        Instant now = now();
        if (tasks == null || tasks.isEmpty()) {
            throw new DomainException("计划执行至少需要一个任务");
        }
        List<PlanRunItem> items = tasks.stream()
                .map(task -> PlanRunItem.pending(task, now))
                .toList();
        return new PlanRun(planId, planName, trigger, PlanRunStatus.RUNNING,
                items, null, now, null, now);
    }

    public static PlanRun restore(PlanRunSnapshot snapshot) {
        PlanRun run = new PlanRun(
                snapshot.planId(),
                snapshot.planName(),
                snapshot.trigger(),
                snapshot.status(),
                snapshot.items().stream().map(PlanRunItem::restore).toList(),
                snapshot.summary(),
                snapshot.startedAt(),
                snapshot.finishedAt(),
                snapshot.createdAt());
        run.id = requireNotNull(snapshot.id(), "计划执行 ID");
        return run;
    }

    public void assignIdentity(PlanRunId assigned) {
        if (id != null) {
            throw new DomainException("计划执行已有 ID，不能重复分配：" + id);
        }
        id = requireNotNull(assigned, "计划执行 ID");
    }

    public void recordDispatched(TaskId taskId) {
        requireRunning("计划执行已经收场，不能再记录派发");
        item(taskId).dispatched(now());
    }

    public void recordDispatchFailure(TaskId taskId, String reason) {
        requireRunning("计划执行已经收场，不能再记录派发失败");
        item(taskId).skipped(reason, now());
    }

    public void observe(TaskId taskId, PlanTaskStatus observed, String detail) {
        requireRunning("计划执行已经收场，不能再更新任务结果");
        item(taskId).observe(observed, detail, now());
    }

    /**
     * 所有任务离开执行态后收敛结果。只要有部分成功就是 PARTIAL；
     * 一条都没成功则整体 FAILED，全部成功才是 COMPLETED。
     */
    public boolean settleIfFinished() {
        if (status != PlanRunStatus.RUNNING || items.stream().anyMatch(item -> item.getStatus().isActive())) {
            return false;
        }
        Instant now = now();
        int succeeded = count(PlanTaskStatus.SUCCEEDED);
        int failed = count(PlanTaskStatus.FAILED);
        int cancelled = count(PlanTaskStatus.CANCELLED);
        int skipped = count(PlanTaskStatus.SKIPPED);
        int unsuccessful = failed + cancelled + skipped;
        if (unsuccessful == 0) {
            status = PlanRunStatus.COMPLETED;
        } else if (succeeded > 0) {
            status = PlanRunStatus.PARTIAL;
        } else {
            status = PlanRunStatus.FAILED;
        }
        summary = buildSummary(succeeded, failed, cancelled, skipped);
        finishedAt = now;
        return true;
    }

    private String buildSummary(int succeeded, int failed, int cancelled, int skipped) {
        StringBuilder text = new StringBuilder()
                .append("计划「").append(planName).append("」执行结束：共 ")
                .append(items.size()).append(" 个任务，成功 ").append(succeeded)
                .append("、失败 ").append(failed)
                .append("、终止 ").append(cancelled)
                .append("、跳过 ").append(skipped).append("。");
        for (PlanRunItem item : items) {
            text.append("\n- #").append(item.getTaskId().value()).append(" ")
                    .append(item.getTitle()).append("：").append(label(item.getStatus()));
            if (item.getDetail() != null) {
                text.append("（").append(item.getDetail()).append("）");
            }
        }
        return text.toString();
    }

    private static String label(PlanTaskStatus status) {
        return switch (status) {
            case SUCCEEDED -> "成功";
            case FAILED -> "失败";
            case CANCELLED -> "终止";
            case SKIPPED -> "跳过";
            case BLOCKED -> "等待决策";
            case RUNNING -> "执行中";
            case PENDING -> "待派发";
        };
    }

    private PlanRunItem item(TaskId taskId) {
        return items.stream()
                .filter(item -> item.getTaskId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new DomainException("计划执行中不包含任务：" + taskId));
    }

    private void requireRunning(String message) {
        if (status != PlanRunStatus.RUNNING) {
            throw new DomainException(message + "，当前状态：" + status);
        }
    }

    public int count(PlanTaskStatus taskStatus) {
        return (int) items.stream().filter(item -> item.getStatus() == taskStatus).count();
    }

    public int progressCount() {
        return (int) items.stream().filter(item -> !item.getStatus().isActive()).count();
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

    public PlanRunSnapshot snapshot() {
        return new PlanRunSnapshot(
                id,
                planId,
                planName,
                trigger,
                status,
                items.stream().map(PlanRunItem::snapshot).toList(),
                summary,
                startedAt,
                finishedAt,
                createdAt);
    }

    public PlanRunId getId() {
        return id;
    }

    public PlanId getPlanId() {
        return planId;
    }

    public String getPlanName() {
        return planName;
    }

    public PlanTrigger getTrigger() {
        return trigger;
    }

    public PlanRunStatus getStatus() {
        return status;
    }

    public List<PlanRunItem> getItems() {
        return List.copyOf(items);
    }

    public String getSummary() {
        return summary;
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
}
