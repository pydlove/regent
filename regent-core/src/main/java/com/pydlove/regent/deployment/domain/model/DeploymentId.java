package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 部署配置强类型 ID。 */
public record DeploymentId(long value) {

    public DeploymentId {
        if (value <= 0) {
            throw new DomainException("部署 ID 必须为正数：" + value);
        }
    }

    public static DeploymentId of(long value) {
        return new DeploymentId(value);
    }
}
