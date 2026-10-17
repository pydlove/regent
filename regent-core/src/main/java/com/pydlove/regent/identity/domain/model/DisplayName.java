package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 用户对外展示的名称，与登录名相互独立。 */
public record DisplayName(String value) {

    private static final int MAX_LENGTH = 32;

    public DisplayName {
        if (value == null || value.isBlank()) {
            throw new DomainException("显示名称不能为空");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new DomainException("显示名称不能超过 " + MAX_LENGTH + " 个字符");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new DomainException("显示名称不能包含控制字符");
        }
    }

    public static DisplayName of(String value) {
        return new DisplayName(value);
    }
}
