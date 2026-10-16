package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/** 环境条目的归属范围。 */
public enum EnvironmentScope {
    GLOBAL,
    PROJECT;

    public static EnvironmentScope parse(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("环境归属不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("环境归属只能是 GLOBAL 或 PROJECT：" + value);
        }
    }
}
