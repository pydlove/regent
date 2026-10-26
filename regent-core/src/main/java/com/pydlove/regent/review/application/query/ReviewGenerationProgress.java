package com.pydlove.regent.review.application.query;

import java.time.Instant;

/** 审阅生成的可观察状态，供任务详情在快照落库前持续展示。 */
public record ReviewGenerationProgress(
        String status,
        String stage,
        String message,
        Instant updatedAt,
        String error) {

    public static ReviewGenerationProgress idle() {
        return new ReviewGenerationProgress(
                "IDLE", "idle", "尚未开始生成审阅", null, null);
    }
}
