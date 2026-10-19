package com.pydlove.regent.deployment.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code deployment} 表写侧数据对象。 */
@Data
public class DeploymentData {

    private Long id;
    private Long projectId;
    private Long serverId;
    private String name;
    private String remoteDirectory;
    private String scriptFilename;
    private String scriptContent;
    private String scriptHash;
    private String executionMode;
    private String projectScriptPath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
