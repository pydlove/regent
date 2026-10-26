package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.Collections;
import java.util.List;

/** 新建或编辑可复用部署服务器。密码只写不读。 */
public record SaveDeploymentServerCommand(
        List<ProjectId> projectIds,
        String name,
        String host,
        int port,
        String username,
        DeploymentAuthType authType,
        String defaultRemoteDirectory,
        String password) {

    /** 兼容不配置默认目录的调用方。 */
    public SaveDeploymentServerCommand(List<ProjectId> projectIds,
                                       String name,
                                       String host,
                                       int port,
                                       String username,
                                       DeploymentAuthType authType,
                                       String password) {
        this(projectIds, name, host, port, username, authType, null, password);
    }

    /** 兼容内部单项目调用；对外接口统一使用 projectIds。 */
    public SaveDeploymentServerCommand(ProjectId projectId,
                                       String name,
                                       String host,
                                       int port,
                                       String username,
                                       DeploymentAuthType authType,
                                       String password) {
        this(Collections.singletonList(projectId), name, host, port, username, authType,
                null, password);
    }

    public SaveDeploymentServerCommand(ProjectId projectId,
                                       String name,
                                       String host,
                                       int port,
                                       String username,
                                       DeploymentAuthType authType,
                                       String defaultRemoteDirectory,
                                       String password) {
        this(Collections.singletonList(projectId), name, host, port, username, authType,
                defaultRemoteDirectory, password);
    }
}
