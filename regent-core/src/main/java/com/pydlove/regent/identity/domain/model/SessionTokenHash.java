package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.regex.Pattern;

/** 会话令牌的 SHA-256；数据库永远不保存可直接使用的明文令牌。 */
public record SessionTokenHash(String value) {

    private static final Pattern HEX = Pattern.compile("[0-9a-f]{64}");

    public SessionTokenHash {
        if (value == null || !HEX.matcher(value).matches()) {
            throw new DomainException("会话令牌哈希格式不正确");
        }
    }
}
