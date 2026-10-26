package com.pydlove.regent.review.interfaces.dto;

import com.pydlove.regent.review.domain.model.ReviewDecision;

import java.time.Instant;

/** 人工验收审计记录。 */
public record ReviewDecisionResponse(
        long id,
        long snapshotId,
        String decision,
        long operatorId,
        String comment,
        Instant createdAt) {

    public static ReviewDecisionResponse from(ReviewDecision decision) {
        return new ReviewDecisionResponse(
                decision.getId().value(),
                decision.getSnapshotId().value(),
                decision.getDecision().name(),
                decision.getOperatorId().value(),
                decision.getComment(),
                decision.getCreatedAt());
    }
}
