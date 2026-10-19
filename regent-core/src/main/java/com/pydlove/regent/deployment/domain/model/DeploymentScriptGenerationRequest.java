package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.util.Objects;

/** Codex 生成部署脚本所需的项目与目标服务器上下文。 */
public record DeploymentScriptGenerationRequest(
        ProjectId projectId,
        String projectCode,
        String projectName,
        String projectPath,
        String techStack,
        String verifyMethod,
        String projectSummary,
        String workspacePath,
        String serverName,
        String serverHost,
        int serverPort,
        String serverUsername,
        String remoteDirectory) {

    public DeploymentScriptGenerationRequest {
        Objects.requireNonNull(projectId, "项目 ID 不能为空");
        requireText(projectCode, "项目标识");
        requireText(projectName, "项目名称");
        requireText(projectPath, "项目路径");
        requireText(workspacePath, "项目文件夹");
        requireText(serverName, "服务器名称");
        requireText(serverHost, "服务器地址");
        requireText(serverUsername, "SSH 用户名");
        requireText(remoteDirectory, "远程目录");
        if (serverPort < 1 || serverPort > 65535) {
            throw new DomainException("SSH 端口必须在 1–65535 之间");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
    }
}
