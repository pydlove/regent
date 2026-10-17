package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 登录会话 ID。 */
public record AuthSessionId(long value) {

    public AuthSessionId {
        if (value <= 0) {
            throw new DomainException("会话 ID 必须为正数：" + value);
        }
    }

    public static AuthSessionId of(long value) {
        return new AuthSessionId(value);
    }
}
