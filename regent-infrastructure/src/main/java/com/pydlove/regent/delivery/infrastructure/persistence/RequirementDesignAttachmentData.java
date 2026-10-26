package com.pydlove.regent.delivery.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** requirement_design_attachment 表读写对象。 */
@Data
public class RequirementDesignAttachmentData {
    private Long id;
    private Long deliveryId;
    private Long moduleId;
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private String storagePath;
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
