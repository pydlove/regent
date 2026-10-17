package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 邮箱验证码摘要。数据库只保存摘要，不保存可直接用于注册的明文验证码。 */
public record EmailVerificationCodeHash(String value) {

    public EmailVerificationCodeHash {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new DomainException("验证码摘要格式不正确");
        }
    }
}
