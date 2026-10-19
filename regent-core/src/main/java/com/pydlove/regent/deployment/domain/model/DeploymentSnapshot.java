package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.util.Objects;

/** 入队时冻结的部署配置，后续编辑不会改写历史执行。 */
public record DeploymentSnapshot(
        DeploymentId deploymentId,
        ProjectId projectId,
        DeploymentServerId serverId,
        String name,
        String remoteDirectory,
        String scriptFilename,
        String scriptContent,
        String scriptHash,
        DeploymentExecutionMode executionMode,
        String projectScriptPath) {

    public DeploymentSnapshot {
        Objects.requireNonNull(deploymentId, "部署 ID 不能为空");
        Objects.requireNonNull(projectId, "归属项目不能为空");
        Objects.requireNonNull(serverId, "服务器 ID 不能为空");
        Objects.requireNonNull(name, "部署名称不能为空");
        Objects.requireNonNull(remoteDirectory, "远程目录不能为空");
        Objects.requireNonNull(scriptFilename, "脚本文件名不能为空");
        Objects.requireNonNull(scriptContent, "部署脚本不能为空");
        Objects.requireNonNull(scriptHash, "脚本摘要不能为空");
        Objects.requireNonNull(executionMode, "部署执行方式不能为空");
        if (executionMode == DeploymentExecutionMode.PROJECT_SCRIPT
                && (projectScriptPath == null || projectScriptPath.isBlank())) {
            throw new DomainException("项目脚本路径不能为空");
        }
    }
}
