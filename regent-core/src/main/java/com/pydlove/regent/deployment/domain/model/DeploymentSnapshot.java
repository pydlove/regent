package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.Objects;

/** 入队时冻结的部署配置，后续编辑不会改写历史执行。 */
public record DeploymentSnapshot(
        DeploymentId deploymentId,
        ProjectId projectId,
        String name,
        String host,
        int port,
        String username,
        DeploymentAuthType authType,
        String remoteDirectory,
        String scriptFilename,
        String scriptContent,
        String scriptHash) {

    public DeploymentSnapshot {
        Objects.requireNonNull(deploymentId, "部署 ID 不能为空");
        Objects.requireNonNull(projectId, "归属项目不能为空");
        Objects.requireNonNull(name, "部署名称不能为空");
        Objects.requireNonNull(host, "服务器地址不能为空");
        Objects.requireNonNull(username, "登录用户不能为空");
        Objects.requireNonNull(authType, "认证方式不能为空");
        Objects.requireNonNull(remoteDirectory, "远程目录不能为空");
        Objects.requireNonNull(scriptFilename, "脚本文件名不能为空");
        Objects.requireNonNull(scriptContent, "部署脚本不能为空");
        Objects.requireNonNull(scriptHash, "脚本摘要不能为空");
    }
}
