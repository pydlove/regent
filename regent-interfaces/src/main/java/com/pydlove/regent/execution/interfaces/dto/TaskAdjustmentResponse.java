package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.TaskAdjustment;

import java.time.Instant;

/** 一条执行中调整方向的对外契约。 */
public record TaskAdjustmentResponse(
        long id,
        long taskId,
        String source,
        Long sourceTaskId,
        String sourceTitle,
        String content,
        String status,
        Long appliedRunId,
        Instant appliedAt,
        Instant createdAt) {

    public static TaskAdjustmentResponse from(TaskAdjustment adjustment) {
        return new TaskAdjustmentResponse(
                adjustment.getId().value(),
                adjustment.getTaskId().value(),
                adjustment.getSource().name(),
                adjustment.getSourceTaskId().map(taskId -> taskId.value()).orElse(null),
                adjustment.getSourceTitle().orElse(null),
                adjustment.getContent(),
                adjustment.getStatus().name(),
                adjustment.getAppliedRunId().map(runId -> runId.value()).orElse(null),
                adjustment.getAppliedAt().orElse(null),
                adjustment.getCreatedAt());
    }
}
