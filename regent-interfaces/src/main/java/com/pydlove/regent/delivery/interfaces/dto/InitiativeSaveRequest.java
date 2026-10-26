package com.pydlove.regent.delivery.interfaces.dto;

public record InitiativeSaveRequest(
        Long projectId,
        Long versionId,
        String title,
        String businessGoal,
        String scopeText,
        String outOfScopeText,
        String successMetrics,
        String stakeholders,
        String targetVersion,
        String riskText,
        String meetingMinutes) {
}
