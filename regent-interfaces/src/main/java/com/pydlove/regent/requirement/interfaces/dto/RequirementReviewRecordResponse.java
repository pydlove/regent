package com.pydlove.regent.requirement.interfaces.dto;

import com.pydlove.regent.requirement.application.query.RequirementReviewRecordView;
import com.pydlove.regent.requirement.domain.model.RequirementReviewCandidate;
import com.pydlove.regent.requirement.domain.model.RequirementReviewRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 线下需求评审记录。 */
public record RequirementReviewRecordResponse(
        long id,
        long versionId,
        int reviewNo,
        LocalDate reviewDate,
        String status,
        String conclusion,
        String minutes,
        String reviewComment,
        List<RequirementSnapshot> requirements,
        long submittedBy,
        Long decidedBy,
        Instant decidedAt,
        Instant createdAt,
        List<RequirementReviewAttachmentResponse> attachments) {

    public static RequirementReviewRecordResponse from(RequirementReviewRecordView view) {
        RequirementReviewRecord record = view.record();
        return new RequirementReviewRecordResponse(
                record.getId().value(),
                record.getVersionId().value(),
                record.getReviewNo(),
                record.getReviewDate(),
                record.getStatus().name(),
                record.getConclusion(),
                record.getMinutes(),
                record.getReviewComment(),
                record.getRequirements().stream()
                        .map(RequirementSnapshot::from)
                        .toList(),
                record.getSubmittedBy().value(),
                record.getDecidedBy() == null ? null : record.getDecidedBy().value(),
                record.getDecidedAt(),
                record.getCreatedAt(),
                view.attachments().stream()
                        .map(RequirementReviewAttachmentResponse::from)
                        .toList());
    }

    public record RequirementSnapshot(
            long requirementId,
            long requirementNo,
            String title) {

        public static RequirementSnapshot from(RequirementReviewCandidate candidate) {
            return new RequirementSnapshot(
                    candidate.requirementId().value(),
                    candidate.requirementNo(),
                    candidate.title());
        }
    }
}
