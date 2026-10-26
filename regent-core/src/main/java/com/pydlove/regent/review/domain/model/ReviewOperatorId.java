package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 审阅操作者的本地强类型引用。 */
public record ReviewOperatorId(long value) {

    public ReviewOperatorId {
        if (value < 1) {
            throw new DomainException("审阅操作者 ID 必须为正数：" + value);
        }
    }

    public static ReviewOperatorId of(long value) {
        return new ReviewOperatorId(value);
    }
}
