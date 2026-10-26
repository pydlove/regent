package com.pydlove.regent.deployment.interfaces.dto;

import com.pydlove.regent.deployment.domain.model.DeploymentRun;

import java.time.Instant;

/** 一次部署执行的完整记录与当前日志快照。 */
public record DeploymentRunDetailResponse(
        long id,
        long deploymentId,
        long projectId,
        Long taskId,
        Long reviewSnapshotId,
        Long reviewDecisionId,
        String projectVersion,
        String versionSource,
        String deploymentName,
        String host,
        int port,
        String username,
        String authType,
        String remoteDirectory,
        String scriptFilename,
        String scriptHash,
        String executionMode,
        String projectScriptPath,
        String status,
        Integer exitCode,
        String summary,
        String log,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt) {

    public static DeploymentRunDetailResponse from(DeploymentRun run, String log) {
        return new DeploymentRunDetailResponse(
                run.getId().value(),
                run.getDeploymentId().value(),
                run.getProjectId().value(),
                run.getReviewReference().map(reference -> reference.taskId()).orElse(null),
                run.getReviewReference().map(reference -> reference.snapshotId()).orElse(null),
                run.getReviewReference().map(reference -> reference.decisionId()).orElse(null),
                run.getProjectVersion(),
                run.getVersionSource(),
                run.getDeploymentName(),
                run.getHost(),
                run.getPort(),
                run.getUsername(),
                run.getAuthType().name(),
                run.getRemoteDirectory(),
                run.getScriptFilename(),
                run.getScriptHash(),
                run.getExecutionMode().name(),
                run.getProjectScriptPath(),
                run.getStatus().name(),
                run.getExitCode(),
                run.getSummary(),
                log,
                run.getQueuedAt(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
