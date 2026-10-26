package com.pydlove.regent.review.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code review_evidence} 表的数据对象。 */
@Data
public class ReviewEvidenceData {

    private Long id;
    private Long snapshotId;
    private Long changeUnitId;
    private String evidenceType;
    private String status;
    private String title;
    private String commandText;
    private String summary;
    private String sourceType;
    private Long sourceId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
