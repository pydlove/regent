package com.pydlove.regent.testing.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 测试用例强类型 ID。 */
public record TestCaseId(long value) {

    public TestCaseId {
        if (value <= 0) {
            throw new DomainException("测试用例 ID 必须为正数：" + value);
        }
    }

    public static TestCaseId of(long value) {
        return new TestCaseId(value);
    }
}
