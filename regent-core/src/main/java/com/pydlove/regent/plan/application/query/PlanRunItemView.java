package com.pydlove.regent.plan.application.query;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.plan.domain.model.PlanTaskStatus;

import java.time.Instant;

/** 计划执行中的单个任务结果。 */
public record PlanRunItemView(
        TaskId taskId,
        String title,
        PlanTaskStatus status,
        String detail,
        Instant updatedAt) {
}
