package com.pydlove.regent.requirement.interfaces.dto;

import com.pydlove.regent.requirement.domain.model.RequirementReviewAttachment;

import java.time.Instant;

/** 需求评审会议纪要附件。 */
public record RequirementReviewAttachmentResponse(
        long id,
        String filename,
        String contentType,
        long sizeBytes,
        String contentUrl,
        long uploadedBy,
        Instant createdAt) {

    public static RequirementReviewAttachmentResponse from(
            RequirementReviewAttachment attachment) {
        return new RequirementReviewAttachmentResponse(
                attachment.id().value(),
                attachment.filename(),
                attachment.contentType(),
                attachment.sizeBytes(),
                "/api/requirement-review-attachments/" + attachment.id().value() + "/content",
                attachment.uploadedBy().value(),
                attachment.createdAt());
    }
}
