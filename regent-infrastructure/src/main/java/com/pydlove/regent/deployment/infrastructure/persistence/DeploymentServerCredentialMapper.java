package com.pydlove.regent.deployment.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

/** 部署服务器密码密文映射。 */
public interface DeploymentServerCredentialMapper {

    DeploymentServerCredentialData selectByServerId(@Param("serverId") long serverId);

    int countByServerId(@Param("serverId") long serverId);

    int upsert(DeploymentServerCredentialData data);

    int deleteByServerId(@Param("serverId") long serverId);
}
