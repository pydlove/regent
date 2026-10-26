package com.pydlove.regent.review.interfaces.dto;

import com.pydlove.regent.review.domain.model.ReviewDecision;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.application.query.ReviewGenerationProgress;

import java.util.List;

/** 任务审阅详情；snapshot 为 null 表示尚未生成。 */
public record TaskReviewResponse(
        ReviewSnapshotResponse snapshot,
        List<ReviewDecisionResponse> decisions,
        ReviewGenerationProgressResponse progress) {

    public static TaskReviewResponse of(ReviewSnapshot snapshot,
                                        List<ReviewDecision> decisions,
                                        ReviewGenerationProgress progress) {
        return new TaskReviewResponse(
                snapshot == null ? null : ReviewSnapshotResponse.from(snapshot),
                decisions.stream().map(ReviewDecisionResponse::from).toList(),
                ReviewGenerationProgressResponse.from(progress));
    }
}
