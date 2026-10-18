package com.pydlove.regent.testing.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/** 测试类型只决定管理界面分类与提示，不限制执行器使用哪种测试框架。 */
public enum TestCaseType {

    WEB,
    API;

    public static TestCaseType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("测试类型不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("无法识别的测试类型：" + raw);
        }
    }
}
