package com.pydlove.regent.deployment.interfaces.dto;

import java.util.List;

/** 新建或编辑部署配置；连接信息和密码由可复用服务器资源维护。 */
public record DeploymentSaveRequest(
        Long projectId,
        List<Long> projectIds,
        Long serverId,
        String name,
        String remoteDirectory,
        String scriptFilename,
        String scriptContent,
        String executionMode,
        String projectScriptPath) {
}
