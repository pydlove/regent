package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.project.domain.model.ProjectId;

/** 新建部署前，根据项目文件夹与远程目录生成脚本。 */
public record GenerateDeploymentScriptCommand(
        ProjectId projectId,
        DeploymentServerId serverId,
        String workspacePath,
        String remoteDirectory) {
}
