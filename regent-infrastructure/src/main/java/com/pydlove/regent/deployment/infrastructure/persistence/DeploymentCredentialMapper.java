package com.pydlove.regent.deployment.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

/** 部署密码密文映射。 */
public interface DeploymentCredentialMapper {

    String selectCiphertext(@Param("deploymentId") long deploymentId);

    int countByDeploymentId(@Param("deploymentId") long deploymentId);

    int upsert(DeploymentCredentialData data);

    int deleteByDeploymentId(@Param("deploymentId") long deploymentId);
}
