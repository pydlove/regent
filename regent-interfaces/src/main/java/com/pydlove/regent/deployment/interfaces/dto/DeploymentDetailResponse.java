package com.pydlove.regent.deployment.interfaces.dto;

import com.pydlove.regent.deployment.application.query.DeploymentOverview;
import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;

import java.time.Instant;
import java.util.List;

/** 部署详情，额外返回可编辑的脚本正文。 */
public record DeploymentDetailResponse(
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
        String scriptContent,
        String executionMode,
        String projectScriptPath,
        boolean credentialConfigured,
        String latestRunStatus,
        Instant latestRunAt,
        Instant createdAt,
        Instant updatedAt) {

    public static DeploymentDetailResponse from(DeploymentOverview overview) {
        Deployment deployment = overview.deployment();
        var server = overview.server();
        DeploymentRun latest = overview.latestRun();
        return new DeploymentDetailResponse(
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
                deployment.getScriptContent(),
                deployment.getExecutionMode().name(),
                deployment.getProjectScriptPath(),
                overview.credentialConfigured(),
                latest == null ? null : latest.getStatus().name(),
                latest == null ? null : latest.getFinishedAt() != null
                        ? latest.getFinishedAt() : latest.getQueuedAt(),
                deployment.getCreatedAt(),
                deployment.getUpdatedAt());
    }
}
