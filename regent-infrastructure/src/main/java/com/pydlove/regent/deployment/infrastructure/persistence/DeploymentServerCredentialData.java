package com.pydlove.regent.deployment.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code deployment_server_credential} 数据对象。 */
@Data
public class DeploymentServerCredentialData {

    private Long serverId;
    private Long aadId;
    private String ciphertext;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
