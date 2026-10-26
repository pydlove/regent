package com.pydlove.regent.execution.domain.repository;

import java.time.Instant;

/** 任务与部署执行统一读模型的一行。字段只服务控制塔展示，不进入领域聚合。 */
public record ExecutionControlTowerRow(
        String executionType,
        long businessId,
        Long runId,
        Integer attemptNo,
        long projectId,
        String projectName,
        String name,
        String businessStatus,
        String runStatus,
        String executor,
        String agentName,
        Integer exitCode,
        String summary,
        Instant startedAt,
        Instant finishedAt,
        Instant updatedAt,
        Instant leaseExpiresAt,
        Instant lastEventAt) {
}
