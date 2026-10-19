package com.pydlove.regent.deployment.interfaces.dto;

import com.pydlove.regent.deployment.domain.model.DeploymentRun;

import java.time.Instant;

/** 部署执行记录列表项，不返回日志正文。 */
public record DeploymentRunSummaryResponse(
        long id,
        long deploymentId,
        long projectId,
        String deploymentName,
        String host,
        int port,
        String username,
        String authType,
        String status,
        Integer exitCode,
        String summary,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt) {

    public static DeploymentRunSummaryResponse from(DeploymentRun run) {
        return new DeploymentRunSummaryResponse(
                run.getId().value(),
                run.getDeploymentId().value(),
                run.getProjectId().value(),
                run.getDeploymentName(),
                run.getHost(),
                run.getPort(),
                run.getUsername(),
                run.getAuthType().name(),
                run.getStatus().name(),
                run.getExitCode(),
                run.getSummary(),
                run.getQueuedAt(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
