package com.pydlove.regent.plan.interfaces.dto;

import com.pydlove.regent.plan.application.query.PlanRunItemView;
import com.pydlove.regent.plan.domain.model.PlanTaskStatus;

import java.time.Instant;

/** 计划执行中的单任务结果响应。 */
public record PlanRunItemResponse(
        long taskId,
        String title,
        PlanTaskStatus status,
        String detail,
        Instant updatedAt) {

    public static PlanRunItemResponse from(PlanRunItemView view) {
        return new PlanRunItemResponse(
                view.taskId().value(),
                view.title(),
                view.status(),
                view.detail(),
                view.updatedAt());
    }
}
