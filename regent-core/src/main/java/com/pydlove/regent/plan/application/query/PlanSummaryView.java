package com.pydlove.regent.plan.application.query;

import com.pydlove.regent.plan.domain.model.PlanId;
import com.pydlove.regent.plan.domain.model.PlanStatus;

import java.time.Instant;

/** 计划列表行。 */
public record PlanSummaryView(
        PlanId planId,
        String name,
        PlanStatus status,
        int taskCount,
        Instant scheduledAt,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        PlanRunView latestRun) {
}
