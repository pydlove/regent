package com.pydlove.regent.deployment.application.query;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;

import java.util.List;

/** 部署配置与其最近一次执行。列表页只需要这一层。 */
public record DeploymentOverview(
        Deployment deployment,
        DeploymentServer server,
        List<String> projectNames,
        DeploymentRun latestRun,
        boolean credentialConfigured) {
}
