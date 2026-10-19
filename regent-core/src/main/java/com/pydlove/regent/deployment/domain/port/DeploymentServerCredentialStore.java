package com.pydlove.regent.deployment.domain.port;

import com.pydlove.regent.deployment.domain.model.DeploymentServerId;

import java.util.Optional;

/** 部署服务器 SSH 密码的独立凭据存储。 */
public interface DeploymentServerCredentialStore {

    void save(DeploymentServerId serverId, String password);

    boolean exists(DeploymentServerId serverId);

    Optional<String> find(DeploymentServerId serverId);

    void delete(DeploymentServerId serverId);
}
