package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 审阅结论 ID。 */
public record ReviewDecisionId(long value) {

    public ReviewDecisionId {
        if (value < 1) {
            throw new DomainException("审阅结论 ID 必须为正数：" + value);
        }
    }

    public static ReviewDecisionId of(long value) {
        return new ReviewDecisionId(value);
    }
}
