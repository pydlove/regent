package com.pydlove.regent.review.interfaces.dto;

import com.pydlove.regent.review.domain.model.ReviewEvidence;

import java.time.Instant;

/** 测试、日志或其它可追溯证据。 */
public record ReviewEvidenceResponse(
        long id,
        Long changeUnitId,
        String type,
        String status,
        String title,
        String command,
        String summary,
        String sourceType,
        Long sourceId,
        Instant occurredAt) {

    public static ReviewEvidenceResponse from(ReviewEvidence evidence) {
        return new ReviewEvidenceResponse(
                evidence.getId().value(),
                evidence.getChangeUnitId() == null ? null : evidence.getChangeUnitId().value(),
                evidence.getType().name(),
                evidence.getStatus().name(),
                evidence.getTitle(),
                evidence.getCommand(),
                evidence.getSummary(),
                evidence.getSourceType(),
                evidence.getSourceId(),
                evidence.getOccurredAt());
    }
}
