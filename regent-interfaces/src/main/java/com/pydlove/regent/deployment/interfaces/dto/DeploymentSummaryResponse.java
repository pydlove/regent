package com.pydlove.regent.deployment.interfaces.dto;

import com.pydlove.regent.deployment.application.query.DeploymentOverview;
import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;

import java.time.Instant;
import java.util.List;

/** 部署列表项，不返回脚本正文。 */
public record DeploymentSummaryResponse(
        long id,
        long projectId,
        String projectName,
        List<Long> projectIds,
        List<String> projectNames,
        long serverId,
        String serverName,
        String name,
        String host,
        int port,
        String username,
        String authType,
        String remoteDirectory,
        String scriptFilename,
        String scriptHash,
        int scriptSize,
        String executionMode,
        String projectScriptPath,
        String latestRunStatus,
        Instant latestRunAt,
        Instant createdAt,
        Instant updatedAt) {

    public static DeploymentSummaryResponse from(DeploymentOverview overview) {
        Deployment deployment = overview.deployment();
        var server = overview.server();
        DeploymentRun latest = overview.latestRun();
        return new DeploymentSummaryResponse(
                deployment.getId().value(),
                deployment.getProjectId().value(),
                overview.projectNames().isEmpty() ? "" : overview.projectNames().get(0),
                deployment.getProjectIds().stream().map(projectId -> projectId.value()).toList(),
                overview.projectNames(),
                server.getId().value(),
                server.getName(),
                deployment.getName(),
                server.getHost(),
                server.getPort(),
                server.getUsername(),
                server.getAuthType().name(),
                deployment.getRemoteDirectory(),
                deployment.getScriptFilename(),
                deployment.getScriptHash(),
                deployment.getScriptSize(),
                deployment.getExecutionMode().name(),
                deployment.getProjectScriptPath(),
                latest == null ? null : latest.getStatus().name(),
                latest == null ? null : latest.getFinishedAt() != null
                        ? latest.getFinishedAt() : latest.getQueuedAt(),
                deployment.getCreatedAt(),
                deployment.getUpdatedAt());
    }
}
