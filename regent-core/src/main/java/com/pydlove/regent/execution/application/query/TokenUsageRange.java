package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/** 工作台 Token 明细的日期窗口。 */
public enum TokenUsageRange {
    TODAY,
    LAST_24_HOURS,
    LAST_7_DAYS,
    LAST_14_DAYS,
    LAST_30_DAYS,
    ALL;

    public static TokenUsageRange parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return LAST_7_DAYS;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("无法识别的 Token 日期范围：" + raw);
        }
    }
}
