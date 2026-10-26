package com.pydlove.regent.requirement.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** requirement_review_attachment 表读写对象。 */
@Data
public class RequirementReviewAttachmentData {

    private Long id;
    private Long recordId;
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private String storagePath;
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
