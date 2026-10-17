package com.pydlove.regent.plan.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;

import java.time.Instant;
import java.util.List;

/** 计划聚合的存储快照。 */
public record PlanSnapshot(
        PlanId id,
        String name,
        List<TaskId> taskIds,
        PlanStatus status,
        Instant scheduledAt,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt) {
}
