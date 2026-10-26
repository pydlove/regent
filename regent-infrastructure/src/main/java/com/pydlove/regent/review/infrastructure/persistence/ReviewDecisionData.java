package com.pydlove.regent.review.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code review_decision} 表的数据对象。 */
@Data
public class ReviewDecisionData {

    private Long id;
    private Long snapshotId;
    private String decisionType;
    private Long operatorId;
    private String commentText;
    private LocalDateTime createdAt;
}
