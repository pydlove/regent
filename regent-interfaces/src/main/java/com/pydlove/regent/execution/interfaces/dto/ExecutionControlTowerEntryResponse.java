package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.query.ExecutionControlTowerEntry;

import java.time.Instant;

/** 控制塔执行行。 */
public record ExecutionControlTowerEntryResponse(
        String executionType,
        long businessId,
        Long runId,
        Integer attemptNo,
        long projectId,
        String projectName,
        String name,
        String businessStatus,
        String healthStatus,
        String stage,
        String statusDetail,
        String deviceName,
        String executor,
        String failureCategory,
        Instant startedAt,
        Instant lastActivityAt,
        Instant finishedAt,
        boolean retryable,
        boolean stoppable,
        boolean reviewable) {

    public static ExecutionControlTowerEntryResponse from(ExecutionControlTowerEntry entry) {
        return new ExecutionControlTowerEntryResponse(
                entry.executionType(),
                entry.businessId(),
                entry.runId(),
                entry.attemptNo(),
                entry.projectId(),
                entry.projectName(),
                entry.name(),
                entry.businessStatus(),
                entry.healthStatus(),
                entry.stage(),
                entry.statusDetail(),
                entry.deviceName(),
                entry.executor(),
                entry.failureCategory(),
                entry.startedAt(),
                entry.lastActivityAt(),
                entry.finishedAt(),
                entry.retryable(),
                entry.stoppable(),
                entry.reviewable());
    }
}
