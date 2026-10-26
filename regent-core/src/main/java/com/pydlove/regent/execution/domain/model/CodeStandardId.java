package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 项目开发规范条目 ID。 */
public record CodeStandardId(long value) {

    public CodeStandardId {
        if (value <= 0) {
            throw new DomainException("开发规范 ID 必须为正数：" + value);
        }
    }

    public static CodeStandardId of(long value) {
        return new CodeStandardId(value);
    }
}
