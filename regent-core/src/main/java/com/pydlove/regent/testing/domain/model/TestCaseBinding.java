package com.pydlove.regent.testing.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/** 某条任务绑定的测试用例快照。 */
public record TestCaseBinding(
        TestCaseId testCaseId,
        String name,
        TestCaseType type,
        String command,
        String scriptFilename,
        String scriptContent,
        String scriptHash,
        boolean enabled,
        boolean deleted,
        Instant boundAt) {

    public TestCaseBinding {
        Objects.requireNonNull(testCaseId, "测试用例 ID 不能为空");
        if (name == null || name.isBlank()) {
            throw new DomainException("测试用例名称不能为空");
        }
        Objects.requireNonNull(type, "测试类型不能为空");
        Objects.requireNonNull(boundAt, "绑定时间不能为空");
    }

    public boolean executable() {
        return enabled && !deleted;
    }
}
