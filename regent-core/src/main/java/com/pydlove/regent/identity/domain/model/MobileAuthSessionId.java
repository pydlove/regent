package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

public record MobileAuthSessionId(long value) {

    public MobileAuthSessionId {
        if (value <= 0) {
            throw new DomainException("手机登录会话 ID 必须大于零");
        }
    }

    public static MobileAuthSessionId of(long value) {
        return new MobileAuthSessionId(value);
    }
}
