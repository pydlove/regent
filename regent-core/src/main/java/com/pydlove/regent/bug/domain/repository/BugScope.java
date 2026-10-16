package com.pydlove.regent.bug.domain.repository;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/** 缺陷列表范围。 */
public enum BugScope {
    ALL,
    OPEN,
    RESOLVED;

    public static BugScope parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("缺陷列表范围无法识别：" + value);
        }
    }
}
