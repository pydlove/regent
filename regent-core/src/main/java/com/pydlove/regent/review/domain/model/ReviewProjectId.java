package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 审阅上下文对项目的本地强类型引用。 */
public record ReviewProjectId(long value) {

    public ReviewProjectId {
        if (value < 1) {
            throw new DomainException("审阅项目 ID 必须为正数：" + value);
        }
    }

    public static ReviewProjectId of(long value) {
        return new ReviewProjectId(value);
    }
}
