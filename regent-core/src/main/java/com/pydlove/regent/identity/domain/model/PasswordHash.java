package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 已编码密码。领域对象只持有它，明文密码不得进入聚合。 */
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new DomainException("密码哈希不能为空");
        }
        if (value.length() > 255) {
            throw new DomainException("密码哈希超长");
        }
    }
}
