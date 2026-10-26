package com.pydlove.regent.requirement.application.query;

import com.pydlove.regent.requirement.domain.model.RequirementReviewAttachment;
import com.pydlove.regent.requirement.domain.model.RequirementReviewRecord;

import java.util.List;

/** 需求评审记录与纪要附件。 */
public record RequirementReviewRecordView(
        RequirementReviewRecord record,
        List<RequirementReviewAttachment> attachments) {

    public RequirementReviewRecordView {
        attachments = List.copyOf(attachments);
    }
}
