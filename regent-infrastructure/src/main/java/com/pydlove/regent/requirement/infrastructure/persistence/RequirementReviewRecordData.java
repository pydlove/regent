package com.pydlove.regent.requirement.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** requirement_review_record 表读写对象。 */
@Data
public class RequirementReviewRecordData {

    private Long id;
    private Long versionId;
    private Integer reviewNo;
    private LocalDate reviewDate;
    private String participants;
    private String conclusion;
    private String minutes;
    private String status;
    private String reviewComment;
    private String requirementsJson;
    private Long reviewedBy;
    private Long decidedBy;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
