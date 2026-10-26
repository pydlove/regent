package com.pydlove.regent.delivery.interfaces.dto;

import com.pydlove.regent.delivery.application.query.InitiativeView;
import com.pydlove.regent.delivery.domain.model.Initiative;

import java.time.Instant;

public record InitiativeResponse(
        long id,
        long projectId,
        Long versionId,
        String projectName,
        String title,
        String businessGoal,
        String scopeText,
        String outOfScopeText,
        String successMetrics,
        String stakeholders,
        String targetVersion,
        String riskText,
        String meetingMinutes,
        String status,
        Instant submittedAt,
        Instant approvedAt,
        Instant rejectedAt,
        Instant createdAt,
        Instant updatedAt,
        InitiativePlanResponse plan) {

    public static InitiativeResponse from(InitiativeView view) {
        Initiative initiative = view.initiative();
        return new InitiativeResponse(
                initiative.getId().value(),
                initiative.getProjectId().value(),
                initiative.getVersionId() == null
                        ? null
                        : initiative.getVersionId().value(),
                view.projectName(),
                initiative.getTitle(),
                initiative.getBusinessGoal(),
                initiative.getScopeText(),
                initiative.getOutOfScopeText(),
                initiative.getSuccessMetrics(),
                initiative.getStakeholders(),
                initiative.getTargetVersion(),
                initiative.getRiskText(),
                initiative.getMeetingMinutes(),
                initiative.getStatus().name(),
                initiative.getSubmittedAt(),
                initiative.getApprovedAt(),
                initiative.getRejectedAt(),
                initiative.getCreatedAt(),
                initiative.getUpdatedAt(),
                InitiativePlanResponse.from(view.plan()));
    }
}
