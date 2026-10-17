package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 登录名。统一转成小写，确保界面上的大小写变化不会造出两个账号。
 */
public record Username(String value) {

    private static final Pattern PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{2,31}");

    public Username {
        if (value == null || value.isBlank()) {
            throw new DomainException("用户名不能为空");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(value).matches()) {
            throw new DomainException("用户名需为 3-32 位小写字母、数字、下划线或连字符，且不能以符号开头");
        }
    }

    public static Username of(String value) {
        return new Username(value);
    }
}
