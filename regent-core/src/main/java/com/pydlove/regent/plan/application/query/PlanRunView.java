package com.pydlove.regent.plan.application.query;

import com.pydlove.regent.plan.domain.model.PlanId;
import com.pydlove.regent.plan.domain.model.PlanRun;
import com.pydlove.regent.plan.domain.model.PlanRunId;
import com.pydlove.regent.plan.domain.model.PlanRunStatus;
import com.pydlove.regent.plan.domain.model.PlanTaskStatus;
import com.pydlove.regent.plan.domain.model.PlanTrigger;

import java.time.Instant;

/** 计划执行摘要。 */
public record PlanRunView(
        PlanRunId runId,
        PlanId planId,
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

    public static PlanRunView from(PlanRun run) {
        return new PlanRunView(
                run.getId(),
                run.getPlanId(),
                run.getPlanName(),
                run.getTrigger(),
                run.getStatus(),
                run.getItems().size(),
                run.progressCount(),
                run.count(PlanTaskStatus.SUCCEEDED),
                run.count(PlanTaskStatus.FAILED),
                run.count(PlanTaskStatus.CANCELLED),
                run.count(PlanTaskStatus.SKIPPED),
                run.count(PlanTaskStatus.RUNNING) + run.count(PlanTaskStatus.PENDING),
                run.count(PlanTaskStatus.BLOCKED),
                run.getSummary(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
