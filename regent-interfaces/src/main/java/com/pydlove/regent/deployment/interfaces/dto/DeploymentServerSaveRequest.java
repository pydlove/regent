package com.pydlove.regent.deployment.interfaces.dto;

/** 新建或编辑可复用部署服务器。密码只写不读。 */
public record DeploymentServerSaveRequest(
        Long projectId,
        String name,
        String host,
        Integer port,
        String username,
        String authType,
        String password) {
}
