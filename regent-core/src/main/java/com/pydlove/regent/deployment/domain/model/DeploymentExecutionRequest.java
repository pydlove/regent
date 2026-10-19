package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.util.Objects;

/** 执行器需要的最小连接上下文。密码只在调用栈里存在，不进入部署聚合。 */
public record DeploymentExecutionRequest(
        DeploymentRunId runId,
        ProjectId projectId,
        String host,
        int port,
        String username,
        DeploymentAuthType authType,
        String remoteDirectory,
        String scriptFilename,
        String scriptContent,
        DeploymentExecutionMode executionMode,
        String projectScriptPath,
        String password,
        String projectDirectory) {

    /** 兼容只执行远端脚本的调用方。 */
    public DeploymentExecutionRequest(DeploymentRunId runId,
                                      ProjectId projectId,
                                      String host,
                                      int port,
                                      String username,
                                      DeploymentAuthType authType,
                                      String remoteDirectory,
                                      String scriptFilename,
                                      String scriptContent,
                                      String password) {
        this(runId, projectId, host, port, username, authType, remoteDirectory,
                scriptFilename, scriptContent, DeploymentExecutionMode.REMOTE_SCRIPT,
                null, password, null);
    }

    public DeploymentExecutionRequest {
        Objects.requireNonNull(runId, "部署执行记录 ID 不能为空");
        Objects.requireNonNull(projectId, "归属项目不能为空");
        Objects.requireNonNull(host, "服务器地址不能为空");
        Objects.requireNonNull(username, "登录用户不能为空");
        Objects.requireNonNull(authType, "认证方式不能为空");
        Objects.requireNonNull(remoteDirectory, "远程目录不能为空");
        Objects.requireNonNull(scriptFilename, "脚本文件名不能为空");
        Objects.requireNonNull(scriptContent, "部署脚本不能为空");
        Objects.requireNonNull(executionMode, "部署执行方式不能为空");
        if (executionMode == DeploymentExecutionMode.PROJECT_SCRIPT) {
            if (projectScriptPath == null || projectScriptPath.isBlank()) {
                throw new DomainException("项目脚本路径不能为空");
            }
            if (projectDirectory == null || projectDirectory.isBlank()) {
                throw new DomainException("项目目录不能为空");
            }
        }
        if (port < 1 || port > 65535) {
            throw new DomainException("SSH 端口必须在 1–65535 之间：" + port);
        }
        if (authType == DeploymentAuthType.PASSWORD
                && (password == null || password.isEmpty())) {
            throw new DomainException("密码认证需要输入服务器密码");
        }
    }
}
