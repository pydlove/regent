package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.project.domain.model.ProjectId;

/** 新建或编辑部署配置。密码只用于写入独立凭据存储，不进入部署聚合。 */
public record SaveDeploymentCommand(
        ProjectId projectId,
        String name,
        String host,
        int port,
        String username,
        DeploymentAuthType authType,
        String remoteDirectory,
        String scriptFilename,
        String scriptContent,
        String password) {
}
