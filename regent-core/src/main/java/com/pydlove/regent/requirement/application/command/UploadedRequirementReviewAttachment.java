package com.pydlove.regent.requirement.application.command;

/** 接口层传入的需求评审纪要附件。 */
public record UploadedRequirementReviewAttachment(
        String filename,
        String contentType,
        byte[] content) {
}
