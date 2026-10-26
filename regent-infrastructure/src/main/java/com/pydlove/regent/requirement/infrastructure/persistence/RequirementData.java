package com.pydlove.regent.requirement.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** requirement 表读写数据对象。 */
@Data
public class RequirementData {

    private Long id;
    private Long projectId;
    private Long initiativeId;
    private Long versionId;
    private String title;
    private String rawText;
    private String acceptanceText;
    private String clarificationText;
    private String status;
    private String poolStatus;
    private String source;
    private String reviewComment;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String clarificationQuestions;
    private String assumptions;
    private String taskDrafts;
    private LocalDateTime plannedAt;
    private LocalDateTime clarifiedAt;
    private Boolean deleted;
    private LocalDateTime deletedAt;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
