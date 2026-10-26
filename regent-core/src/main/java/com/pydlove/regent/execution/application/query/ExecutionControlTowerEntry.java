package com.pydlove.regent.execution.application.query;

import java.time.Instant;

/** 控制塔中的一条执行。 */
public record ExecutionControlTowerEntry(
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
}
