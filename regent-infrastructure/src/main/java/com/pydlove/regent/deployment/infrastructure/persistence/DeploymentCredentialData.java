package com.pydlove.regent.deployment.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code deployment_credential} 表写侧数据对象。 */
@Data
public class DeploymentCredentialData {

    private Long deploymentId;
    private String ciphertext;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
