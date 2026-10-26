package com.pydlove.regent.delivery.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** initiative_attachment 表读写对象。 */
@Data
public class InitiativeAttachmentData {
    private Long id;
    private Long initiativeId;
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private String storagePath;
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
