package com.pydlove.regent.plan.interfaces.dto;

import com.pydlove.regent.plan.application.query.PlanSummaryView;
import com.pydlove.regent.plan.domain.model.PlanStatus;

import java.time.Instant;

/** 计划列表行响应。 */
public record PlanSummaryResponse(
        long id,
        String name,
        PlanStatus status,
        int taskCount,
        Instant scheduledAt,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        PlanRunResponse latestRun) {

    public static PlanSummaryResponse from(PlanSummaryView view) {
        return new PlanSummaryResponse(
                view.planId().value(),
                view.name(),
                view.status(),
                view.taskCount(),
                view.scheduledAt(),
                view.startedAt(),
                view.finishedAt(),
                view.createdAt(),
                view.latestRun() == null ? null : PlanRunResponse.from(view.latestRun()));
    }
}
