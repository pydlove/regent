package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 平台用户 ID。 */
public record UserId(long value) {

    public UserId {
        if (value <= 0) {
            throw new DomainException("用户 ID 必须为正数：" + value);
        }
    }

    public static UserId of(long value) {
        return new UserId(value);
    }
}
