package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 审阅发现 ID。 */
public record ReviewFindingId(long value) {

    public ReviewFindingId {
        if (value < 1) {
            throw new DomainException("审阅发现 ID 必须为正数：" + value);
        }
    }

    public static ReviewFindingId of(long value) {
        return new ReviewFindingId(value);
    }
}
