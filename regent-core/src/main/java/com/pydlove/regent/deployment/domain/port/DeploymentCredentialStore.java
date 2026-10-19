package com.pydlove.regent.deployment.domain.port;

import com.pydlove.regent.deployment.domain.model.DeploymentId;

import java.util.Optional;

/**
 * 部署 SSH 密码的独立凭据存储。
 *
 * <p>密码不属于部署聚合，也不会通过查询接口回传。实现负责加密持久化，
 * 调用方只看到“是否存在”和本次执行所需的明文。
 */
public interface DeploymentCredentialStore {

    void save(DeploymentId deploymentId, String password);

    boolean exists(DeploymentId deploymentId);

    Optional<String> find(DeploymentId deploymentId);

    void delete(DeploymentId deploymentId);
}
