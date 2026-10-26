package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 审阅证据 ID。 */
public record ReviewEvidenceId(long value) {

    public ReviewEvidenceId {
        if (value < 1) {
            throw new DomainException("审阅证据 ID 必须为正数：" + value);
        }
    }

    public static ReviewEvidenceId of(long value) {
        return new ReviewEvidenceId(value);
    }
}
