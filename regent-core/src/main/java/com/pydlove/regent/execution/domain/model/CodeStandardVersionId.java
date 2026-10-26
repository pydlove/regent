package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 开发规范版本 ID。 */
public record CodeStandardVersionId(long value) {

    public CodeStandardVersionId {
        if (value <= 0) {
            throw new DomainException("开发规范版本 ID 必须为正数：" + value);
        }
    }

    public static CodeStandardVersionId of(long value) {
        return new CodeStandardVersionId(value);
    }
}
