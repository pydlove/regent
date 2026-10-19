package com.pydlove.regent.deployment.interfaces.dto;

/** Codex 生成部署脚本所需的表单上下文。 */
public record DeploymentScriptGenerationRequest(
        String requestId,
        Long projectId,
        Long serverId,
        String workspacePath,
        String remoteDirectory) {
}
