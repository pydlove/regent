package com.pydlove.regent.delivery.interfaces.dto;

import com.pydlove.regent.delivery.domain.model.InitiativeAttachment;

import java.time.Instant;

/** 立项会议纪要附件对外契约。 */
public record InitiativeAttachmentResponse(
        long id,
        long initiativeId,
        String filename,
        String contentType,
        long sizeBytes,
        String contentUrl,
        long uploadedBy,
        Instant createdAt) {

    public static InitiativeAttachmentResponse from(InitiativeAttachment attachment) {
        return new InitiativeAttachmentResponse(
                attachment.id().value(),
                attachment.initiativeId().value(),
                attachment.filename(),
                attachment.contentType(),
                attachment.sizeBytes(),
                "/api/initiative-attachments/" + attachment.id().value() + "/content",
                attachment.uploadedBy().value(),
                attachment.createdAt());
    }
}
