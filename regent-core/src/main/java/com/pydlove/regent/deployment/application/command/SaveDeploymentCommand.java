package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.project.domain.model.ProjectId;

/** 新建或编辑部署配置。服务器连接与密码由服务器资源维护。 */
public record SaveDeploymentCommand(
        ProjectId projectId,
        DeploymentServerId serverId,
        String name,
        String remoteDirectory,
        String scriptFilename,
        String scriptContent) {
}
