package com.pydlove.regent.deployment.interfaces.dto;

/** 新建或编辑部署配置。密码只写入不读取，响应永远不回传明文。 */
public record DeploymentSaveRequest(
        Long projectId,
        String name,
        String host,
        Integer port,
        String username,
        String authType,
        String remoteDirectory,
        String scriptFilename,
        String scriptContent,
        String password) {
}
