package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 变更单元 ID。 */
public record ReviewChangeUnitId(long value) {

    public ReviewChangeUnitId {
        if (value < 1) {
            throw new DomainException("变更单元 ID 必须为正数：" + value);
        }
    }

    public static ReviewChangeUnitId of(long value) {
        return new ReviewChangeUnitId(value);
    }
}
