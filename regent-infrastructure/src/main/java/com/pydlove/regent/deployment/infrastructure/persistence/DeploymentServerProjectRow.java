package com.pydlove.regent.deployment.infrastructure.persistence;

import lombok.Data;

/** {@code deployment_server_project} 的服务器到项目关联。 */
@Data
public class DeploymentServerProjectRow {

    private Long serverId;
    private Long projectId;
}
