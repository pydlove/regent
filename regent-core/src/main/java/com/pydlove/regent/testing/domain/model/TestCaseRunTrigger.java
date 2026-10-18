package com.pydlove.regent.testing.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/** 测试由什么触发：任务完成后的自动验证，或菜单里的手动执行。 */
public enum TestCaseRunTrigger {

    TASK_COMPLETION,
    MANUAL;

    public static TestCaseRunTrigger parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("测试触发类型不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("无法识别的测试触发类型：" + raw);
        }
    }
}
