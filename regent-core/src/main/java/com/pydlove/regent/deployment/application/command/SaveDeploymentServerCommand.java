package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.project.domain.model.ProjectId;

/** 新建或编辑可复用部署服务器。密码只写不读。 */
public record SaveDeploymentServerCommand(
        ProjectId projectId,
        String name,
        String host,
        int port,
        String username,
        DeploymentAuthType authType,
        String defaultRemoteDirectory,
        String password) {

    /** 兼容不配置默认目录的调用方。 */
    public SaveDeploymentServerCommand(ProjectId projectId,
                                       String name,
                                       String host,
                                       int port,
                                       String username,
                                       DeploymentAuthType authType,
                                       String password) {
        this(projectId, name, host, port, username, authType, null, password);
    }
}
