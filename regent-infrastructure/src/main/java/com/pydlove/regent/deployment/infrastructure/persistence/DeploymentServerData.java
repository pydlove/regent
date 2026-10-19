package com.pydlove.regent.deployment.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code deployment_server} 写侧数据对象。 */
@Data
public class DeploymentServerData {

    private Long id;
    private Long projectId;
    private String name;
    private String host;
    private Integer port;
    private String sshUsername;
    private String authType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
