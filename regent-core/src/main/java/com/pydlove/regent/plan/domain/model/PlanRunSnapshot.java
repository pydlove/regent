package com.pydlove.regent.plan.domain.model;

import java.time.Instant;
import java.util.List;

/** 计划执行记录及其任务结果的存储快照。 */
public record PlanRunSnapshot(
        PlanRunId id,
        PlanId planId,
        String planName,
        PlanTrigger trigger,
        PlanRunStatus status,
        List<PlanRunItemSnapshot> items,
        String summary,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt) {
}
