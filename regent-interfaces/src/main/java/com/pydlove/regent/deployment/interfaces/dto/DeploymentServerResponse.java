package com.pydlove.regent.deployment.interfaces.dto;

import com.pydlove.regent.deployment.application.query.DeploymentServerOverview;

import java.time.Instant;
import java.util.List;

/** 可复用部署服务器；永远不回传密码明文。 */
public record DeploymentServerResponse(
        long id,
        List<Long> projectIds,
        String name,
        String host,
        int port,
        String username,
        String authType,
        String defaultRemoteDirectory,
        boolean credentialConfigured,
        int deploymentCount,
        Instant createdAt,
        Instant updatedAt) {

    public static DeploymentServerResponse from(DeploymentServerOverview overview) {
        var server = overview.server();
        return new DeploymentServerResponse(
                server.getId().value(),
                server.getProjectIds().stream().map(projectId -> projectId.value()).toList(),
                server.getName(),
                server.getHost(),
                server.getPort(),
                server.getUsername(),
                server.getAuthType().name(),
                server.getDefaultRemoteDirectory(),
                overview.credentialConfigured(),
                overview.deploymentCount(),
                server.getCreatedAt(),
                server.getUpdatedAt());
    }
}
