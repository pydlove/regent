package com.pydlove.regent.deployment.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code deployment} 表读侧数据对象。 */
@Data
public class DeploymentRow {

    private Long id;
    private Long projectId;
    private String name;
    private String host;
    private Integer port;
    private String sshUsername;
    private String authType;
    private String remoteDirectory;
    private String scriptFilename;
    private String scriptContent;
    private String scriptHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
