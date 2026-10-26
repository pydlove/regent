package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.DeploymentExecutionMode;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;

/** 新建或编辑部署配置。服务器连接与密码由服务器资源维护。 */
public record SaveDeploymentCommand(
        List<ProjectId> projectIds,
        DeploymentServerId serverId,
        String name,
        String remoteDirectory,
        String scriptFilename,
        String scriptContent,
        DeploymentExecutionMode executionMode,
        String projectScriptPath) {

    public SaveDeploymentCommand(ProjectId projectId,
                                 DeploymentServerId serverId,
                                 String name,
                                 String remoteDirectory,
                                 String scriptFilename,
                                 String scriptContent) {
        this(List.of(projectId), serverId, name, remoteDirectory, scriptFilename, scriptContent,
                DeploymentExecutionMode.REMOTE_SCRIPT, null);
    }

    public SaveDeploymentCommand(ProjectId projectId,
                                 DeploymentServerId serverId,
                                 String name,
                                 String remoteDirectory,
                                 String scriptFilename,
                                 String scriptContent,
                                 DeploymentExecutionMode executionMode,
                                 String projectScriptPath) {
        this(List.of(projectId), serverId, name, remoteDirectory, scriptFilename, scriptContent,
                executionMode, projectScriptPath);
    }

    public SaveDeploymentCommand(List<ProjectId> projectIds,
                                 DeploymentServerId serverId,
                                 String name,
                                 String remoteDirectory,
                                 String scriptFilename,
                                 String scriptContent) {
        this(projectIds, serverId, name, remoteDirectory, scriptFilename, scriptContent,
                DeploymentExecutionMode.REMOTE_SCRIPT, null);
    }

    public SaveDeploymentCommand {
        projectIds = projectIds == null ? List.of() : List.copyOf(projectIds);
    }

    /** 兼容只关心主项目的执行路径；新代码应使用完整的 projectIds。 */
    public ProjectId projectId() {
        return projectIds.isEmpty() ? null : projectIds.get(0);
    }
}
