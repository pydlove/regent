package com.pydlove.regent.plan.interfaces.dto;

import com.pydlove.regent.plan.application.query.PlanRunView;
import com.pydlove.regent.plan.domain.model.PlanRunStatus;
import com.pydlove.regent.plan.domain.model.PlanTrigger;

import java.time.Instant;

/** 计划执行摘要响应。 */
public record PlanRunResponse(
        long id,
        long planId,
        String planName,
        PlanTrigger trigger,
        PlanRunStatus status,
        int taskCount,
        int progressCount,
        int succeededCount,
        int failedCount,
        int cancelledCount,
        int skippedCount,
        int runningCount,
        int blockedCount,
        String summary,
        Instant startedAt,
        Instant finishedAt) {

    public static PlanRunResponse from(PlanRunView view) {
        return new PlanRunResponse(
                view.runId().value(),
                view.planId().value(),
                view.planName(),
                view.trigger(),
                view.status(),
                view.taskCount(),
                view.progressCount(),
                view.succeededCount(),
                view.failedCount(),
                view.cancelledCount(),
                view.skippedCount(),
                view.runningCount(),
                view.blockedCount(),
                view.summary(),
                view.startedAt(),
                view.finishedAt());
    }
}
