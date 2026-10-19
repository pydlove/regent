package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 可复用部署服务器强类型 ID。 */
public record DeploymentServerId(long value) {

    public DeploymentServerId {
        if (value <= 0) {
            throw new DomainException("部署服务器 ID 必须为正数：" + value);
        }
    }

    public static DeploymentServerId of(long value) {
        return new DeploymentServerId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
