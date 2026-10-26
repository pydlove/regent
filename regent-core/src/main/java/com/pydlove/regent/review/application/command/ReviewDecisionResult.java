package com.pydlove.regent.review.application.command;

import com.pydlove.regent.review.domain.model.ReviewDecision;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;

import java.util.Optional;

/** 结论落库结果；要求修改时会附带自动创建的原会话后续任务。 */
public record ReviewDecisionResult(
        ReviewSnapshot snapshot,
        ReviewDecision decision,
        Long continuationTaskId) {

    public Optional<Long> continuation() {
        return Optional.ofNullable(continuationTaskId);
    }
}
