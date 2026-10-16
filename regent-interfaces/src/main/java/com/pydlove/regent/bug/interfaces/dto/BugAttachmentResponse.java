package com.pydlove.regent.bug.interfaces.dto;

import com.pydlove.regent.bug.domain.model.BugAttachment;

/** Bug 图片附件的对外契约。 */
public record BugAttachmentResponse(
        long id,
        long bugId,
        String filename,
        String contentType,
        long sizeBytes,
        String contentUrl) {

    public static BugAttachmentResponse from(BugAttachment attachment) {
        return new BugAttachmentResponse(
                attachment.id().value(),
                attachment.bugId().value(),
                attachment.filename(),
                attachment.contentType(),
                attachment.sizeBytes(),
                "/api/bug-attachments/" + attachment.id().value() + "/content");
    }
}
