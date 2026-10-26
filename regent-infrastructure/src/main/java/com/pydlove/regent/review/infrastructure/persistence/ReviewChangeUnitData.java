package com.pydlove.regent.review.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code review_change_unit} 表的数据对象。 */
@Data
public class ReviewChangeUnitData {

    private Long id;
    private Long snapshotId;
    private Integer sequenceNo;
    private String kind;
    private String riskLevel;
    private String title;
    private String reason;
    private String decisionQuestion;
    private String moduleName;
    private String filePathsJson;
    private String keyHunksJson;
    private Boolean requiresView;
    private LocalDateTime viewedAt;
    private String decisionStatus;
    private String decisionComment;
    private Long decidedBy;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
}
