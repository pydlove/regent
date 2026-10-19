package com.pydlove.regent.deployment.application.query;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;

/** 部署配置与其最近一次执行。列表页只需要这一层。 */
public record DeploymentOverview(
        Deployment deployment,
        String projectName,
        DeploymentRun latestRun,
        boolean credentialConfigured) {
}
