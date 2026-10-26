package com.pydlove.regent.deployment.domain.model;

import java.util.Objects;

/** 入队时冻结的服务器连接信息。 */
public record DeploymentServerSnapshot(
        DeploymentServerId serverId,
        String name,
        String host,
        int port,
        String username,
        DeploymentAuthType authType) {

    public DeploymentServerSnapshot {
        Objects.requireNonNull(serverId, "服务器 ID 不能为空");
        Objects.requireNonNull(name, "服务器名称不能为空");
        Objects.requireNonNull(host, "服务器地址不能为空");
        Objects.requireNonNull(username, "登录用户不能为空");
        Objects.requireNonNull(authType, "认证方式不能为空");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("SSH 端口必须在 1–65535 之间：" + port);
        }
    }
}
