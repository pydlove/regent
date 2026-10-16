package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 环境条目的强类型 ID。 */
public record EnvironmentId(long value) {

    public EnvironmentId {
        if (value <= 0) {
            throw new DomainException("环境条目 ID 必须为正数：" + value);
        }
    }

    public static EnvironmentId of(long value) {
        return new EnvironmentId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
