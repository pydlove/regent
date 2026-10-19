package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 部署执行记录强类型 ID。 */
public record DeploymentRunId(long value) {

    public DeploymentRunId {
        if (value <= 0) {
            throw new DomainException("部署执行记录 ID 必须为正数：" + value);
        }
    }

    public static DeploymentRunId of(long value) {
        return new DeploymentRunId(value);
    }
}
