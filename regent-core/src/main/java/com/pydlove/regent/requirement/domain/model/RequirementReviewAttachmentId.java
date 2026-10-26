package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 需求评审纪要附件 ID。 */
public record RequirementReviewAttachmentId(long value) {

    public RequirementReviewAttachmentId {
        if (value <= 0) {
            throw new DomainException("需求评审附件 ID 必须为正数：" + value);
        }
    }

    public static RequirementReviewAttachmentId of(long value) {
        return new RequirementReviewAttachmentId(value);
    }
}
