package com.pydlove.regent.review.interfaces.dto;

import com.pydlove.regent.review.application.command.ReviewDecisionResult;

/** 验收动作结果；要求修改时 continuationTaskId 指向原会话后续任务。 */
public record ReviewDecisionActionResponse(
        ReviewSnapshotResponse snapshot,
        ReviewDecisionResponse decision,
        Long continuationTaskId) {

    public static ReviewDecisionActionResponse from(ReviewDecisionResult result) {
        return new ReviewDecisionActionResponse(
                ReviewSnapshotResponse.from(result.snapshot()),
                ReviewDecisionResponse.from(result.decision()),
                result.continuationTaskId());
    }
}
