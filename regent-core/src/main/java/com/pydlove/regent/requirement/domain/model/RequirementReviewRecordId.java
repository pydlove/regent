package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 需求评审记录 ID。 */
public record RequirementReviewRecordId(long value) {

    public RequirementReviewRecordId {
        if (value <= 0) {
            throw new DomainException("需求评审记录 ID 必须为正数：" + value);
        }
    }

    public static RequirementReviewRecordId of(long value) {
        return new RequirementReviewRecordId(value);
    }
}
