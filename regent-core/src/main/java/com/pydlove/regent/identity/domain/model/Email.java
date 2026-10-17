package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;
import java.util.regex.Pattern;

/** 注册邮箱。统一小写并去掉首尾空白，避免同一个邮箱出现多种写法。 */
public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    private static final int MAX_LOCAL_PART_LENGTH = 64;
    private static final Pattern PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new DomainException("邮箱不能为空");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new DomainException("邮箱不能超过 " + MAX_LENGTH + " 个字符");
        }
        int at = value.indexOf('@');
        if (at <= 0 || at > MAX_LOCAL_PART_LENGTH || !PATTERN.matcher(value).matches()) {
            throw new DomainException("邮箱格式不正确");
        }
    }

    public static Email of(String value) {
        return new Email(value);
    }

    public String localPart() {
        return value.substring(0, value.indexOf('@'));
    }
}
