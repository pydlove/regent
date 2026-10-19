package com.pydlove.regent.deployment.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code deployment_run} 表读侧数据对象。 */
@Data
public class DeploymentRunRow {

    private Long id;
    private Long deploymentId;
    private Long projectId;
    private String deploymentName;
    private String host;
    private Integer port;
    private String sshUsername;
    private String authType;
    private String remoteDirectory;
    private String scriptFilename;
    private String scriptContent;
    private String scriptHash;
    private String status;
    private Integer exitCode;
    private String summary;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
