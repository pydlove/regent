package com.pydlove.regent.review.interfaces.dto;

import com.pydlove.regent.review.domain.model.ReviewChangeUnit;

import java.time.Instant;
import java.util.List;

/** 按业务意图聚合的审阅单元。 */
public record ReviewChangeUnitResponse(
        long id,
        int sequenceNo,
        String kind,
        String riskLevel,
        String title,
        String reason,
        String decisionQuestion,
        String moduleName,
        List<String> filePaths,
        List<ReviewHunkResponse> keyHunks,
        boolean requiresView,
        Instant viewedAt,
        String decisionStatus,
        String decisionComment,
        long decidedBy,
        Instant decidedAt) {

    public static ReviewChangeUnitResponse from(ReviewChangeUnit unit) {
        return new ReviewChangeUnitResponse(
                unit.getId().value(),
                unit.getSequenceNo(),
                unit.getKind().name(),
                unit.getRiskLevel().name(),
                unit.getTitle(),
                unit.getReason(),
                unit.getDecisionQuestion(),
                unit.getModuleName(),
                unit.getFilePaths(),
                unit.getKeyHunks().stream().map(ReviewHunkResponse::from).toList(),
                unit.isRequiresView(),
                unit.getViewedAt(),
                unit.getDecisionStatus().name(),
                unit.getDecisionComment(),
                unit.getDecidedBy() == null ? 0 : unit.getDecidedBy().value(),
                unit.getDecidedAt());
    }
}
