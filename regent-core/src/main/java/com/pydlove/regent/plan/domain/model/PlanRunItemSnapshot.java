package com.pydlove.regent.plan.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;

import java.time.Instant;

/** 计划内单个任务的执行结果快照。 */
public record PlanRunItemSnapshot(
        TaskId taskId,
        String title,
        PlanTaskStatus status,
        String detail,
        Instant updatedAt) {
}
