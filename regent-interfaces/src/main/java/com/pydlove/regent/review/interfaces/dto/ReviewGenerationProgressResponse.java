package com.pydlove.regent.review.interfaces.dto;

import com.pydlove.regent.review.application.query.ReviewGenerationProgress;

import java.time.Instant;

/** 审阅生成进度，快照生成前也会返回。 */
public record ReviewGenerationProgressResponse(
        String status,
        String stage,
        String message,
        Instant updatedAt,
        String error) {

    public static ReviewGenerationProgressResponse from(ReviewGenerationProgress progress) {
        return new ReviewGenerationProgressResponse(
                progress.status(),
                progress.stage(),
                progress.message(),
                progress.updatedAt(),
                progress.error());
    }
}
