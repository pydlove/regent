package com.pydlove.regent.review.interfaces.dto;

import com.pydlove.regent.review.domain.model.ReviewFinding;

import java.time.Instant;

/** 可定位到文件与行号的审阅发现。 */
public record ReviewFindingResponse(
        long id,
        Long changeUnitId,
        String severity,
        String status,
        String title,
        String description,
        String impact,
        String filePath,
        Integer lineStart,
        Integer lineEnd,
        String suggestedAction,
        Instant createdAt,
        Instant updatedAt) {

    public static ReviewFindingResponse from(ReviewFinding finding) {
        return new ReviewFindingResponse(
                finding.getId().value(),
                finding.getChangeUnitId() == null ? null : finding.getChangeUnitId().value(),
                finding.getSeverity().name(),
                finding.getStatus().name(),
                finding.getTitle(),
                finding.getDescription(),
                finding.getImpact(),
                finding.getFilePath(),
                finding.getLineStart(),
                finding.getLineEnd(),
                finding.getSuggestedAction(),
                finding.getCreatedAt(),
                finding.getUpdatedAt());
    }
}
