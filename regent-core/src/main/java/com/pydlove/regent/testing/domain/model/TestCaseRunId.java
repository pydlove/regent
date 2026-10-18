package com.pydlove.regent.testing.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 测试执行记录 ID。 */
public record TestCaseRunId(long value) {

    public TestCaseRunId {
        if (value <= 0) {
            throw new DomainException("测试执行记录 ID 必须为正数：" + value);
        }
    }

    public static TestCaseRunId of(long value) {
        return new TestCaseRunId(value);
    }
}
