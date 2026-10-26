package com.pydlove.regent.requirement.interfaces.dto;

import com.pydlove.regent.requirement.application.query.RequirementView;
import com.pydlove.regent.requirement.domain.model.Requirement;

import java.time.Instant;
import java.util.List;

/** 需求详情的对外契约。 */
public record RequirementResponse(
        long id,
        long projectId,
        Long initiativeId,
        Long versionId,
        String title,
        String rawText,
        String acceptanceText,
        String clarificationText,
        String status,
        String poolStatus,
        String source,
        String reviewComment,
        Long reviewedBy,
        Instant reviewedAt,
        List<String> clarificationQuestions,
        List<String> assumptions,
        List<RequirementTaskDraftResponse> taskDrafts,
        List<RequirementTaskResponse> tasks,
        Instant createdAt,
        Instant updatedAt,
        Instant plannedAt) {

    public static RequirementResponse from(RequirementView view) {
        Requirement requirement = view.requirement();
        return new RequirementResponse(
                requirement.getId().value(),
                requirement.getProjectId().value(),
                requirement.getInitiativeId() == null
                        ? null
                        : requirement.getInitiativeId().value(),
                requirement.getVersionId() == null
                        ? null
                        : requirement.getVersionId().value(),
                requirement.getTitle(),
                requirement.getRawText(),
                requirement.getAcceptanceText(),
                requirement.getClarificationText(),
                view.effectiveStatus().name(),
                requirement.getPoolStatus().name(),
                requirement.getSource().name(),
                requirement.getReviewComment(),
                requirement.getReviewedBy() == null ? null : requirement.getReviewedBy().value(),
                requirement.getReviewedAt(),
                requirement.getClarificationQuestions(),
                requirement.getAssumptions(),
                requirement.getTaskDrafts().stream()
                        .map(RequirementTaskDraftResponse::from)
                        .toList(),
                view.tasks().stream()
                        .map(task -> RequirementTaskResponse.from(
                                task,
                                view.dependencies().getOrDefault(task.getId().value(), List.of())))
                        .toList(),
                requirement.getCreatedAt(),
                requirement.getUpdatedAt(),
                requirement.getPlannedAt());
    }
}
