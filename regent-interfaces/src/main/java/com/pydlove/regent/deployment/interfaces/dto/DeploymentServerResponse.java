package com.pydlove.regent.deployment.interfaces.dto;

import com.pydlove.regent.deployment.application.query.DeploymentServerOverview;

import java.time.Instant;

/** 可复用部署服务器；永远不回传密码明文。 */
public record DeploymentServerResponse(
        long id,
        long projectId,
        String name,
        String host,
        int port,
        String username,
        String authType,
        boolean credentialConfigured,
        int deploymentCount,
        Instant createdAt,
        Instant updatedAt) {

    public static DeploymentServerResponse from(DeploymentServerOverview overview) {
        var server = overview.server();
        return new DeploymentServerResponse(
                server.getId().value(),
                server.getProjectId().value(),
                server.getName(),
                server.getHost(),
                server.getPort(),
                server.getUsername(),
                server.getAuthType().name(),
                overview.credentialConfigured(),
                overview.deploymentCount(),
                server.getCreatedAt(),
                server.getUpdatedAt());
    }
}
