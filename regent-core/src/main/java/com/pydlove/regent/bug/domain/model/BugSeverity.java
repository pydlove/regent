package com.pydlove.regent.bug.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/** 缺陷严重级。 */
public enum BugSeverity {
    BLOCKER,
    MAJOR,
    MINOR,
    TRIVIAL;

    public static BugSeverity parse(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("缺陷严重级不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("缺陷严重级无法识别：" + value);
        }
    }
}
