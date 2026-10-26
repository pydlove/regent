package com.pydlove.regent.deployment.interfaces.dto;

import java.util.List;

/** 新建或编辑可复用部署服务器。密码只写不读。 */
public record DeploymentServerSaveRequest(
        List<Long> projectIds,
        String name,
        String host,
        Integer port,
        String username,
        String authType,
        String defaultRemoteDirectory,
        String password) {
}
