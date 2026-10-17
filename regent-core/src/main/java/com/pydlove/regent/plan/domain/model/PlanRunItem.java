package com.pydlove.regent.plan.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/** 计划内一条任务的执行状态。只由 {@link PlanRun} 推进。 */
public class PlanRunItem {

    private final TaskId taskId;
    private final String title;
    private PlanTaskStatus status;
    private String detail;
    private Instant updatedAt;

    private PlanRunItem(TaskId taskId, String title, PlanTaskStatus status, String detail, Instant updatedAt) {
        this.taskId = requireNotNull(taskId, "任务 ID");
        this.title = requireNotNull(title, "任务标题");
        this.status = requireNotNull(status, "任务状态");
        this.detail = detail;
        this.updatedAt = requireNotNull(updatedAt, "更新时间");
    }

    static PlanRunItem pending(PlanTaskSnapshot snapshot, Instant now) {
        return new PlanRunItem(snapshot.taskId(), snapshot.title(), PlanTaskStatus.PENDING, null, now);
    }

    static PlanRunItem restore(PlanRunItemSnapshot snapshot) {
        return new PlanRunItem(
                snapshot.taskId(),
                snapshot.title(),
                snapshot.status(),
                snapshot.detail(),
                snapshot.updatedAt());
    }

    void dispatched(Instant now) {
        requireActive("任务已经收场，不能再标记为已派发");
        status = PlanTaskStatus.RUNNING;
        updatedAt = now;
    }

    void skipped(String reason, Instant now) {
        requireActive("任务已经收场，不能再标记为跳过");
        status = PlanTaskStatus.SKIPPED;
        detail = normalizeDetail(reason);
        updatedAt = now;
    }

    void observe(PlanTaskStatus observed, String observedDetail, Instant now) {
        if (!status.isActive()) {
            return;
        }
        if (observed == PlanTaskStatus.PENDING || observed == PlanTaskStatus.SKIPPED) {
            throw new DomainException("任务观察状态不合法：" + observed);
        }
        status = observed;
        if (observedDetail != null && !observedDetail.isBlank()) {
            detail = normalizeDetail(observedDetail);
        }
        updatedAt = now;
    }

    private void requireActive(String message) {
        if (!status.isActive()) {
            throw new DomainException(message + "，当前状态：" + status);
        }
    }

    private static String normalizeDetail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String oneLine = value.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 240 ? oneLine : oneLine.substring(0, 240) + "…";
    }

    PlanRunItemSnapshot snapshot() {
        return new PlanRunItemSnapshot(taskId, title, status, detail, updatedAt);
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public PlanTaskStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
