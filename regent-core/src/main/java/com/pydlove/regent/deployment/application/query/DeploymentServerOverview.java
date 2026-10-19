package com.pydlove.regent.deployment.application.query;

import com.pydlove.regent.deployment.domain.model.DeploymentServer;

/** 部署服务器与凭据状态。 */
public record DeploymentServerOverview(
        DeploymentServer server,
        boolean credentialConfigured,
        int deploymentCount) {
}
