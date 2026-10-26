package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;

/** 需求分析上下文。 */
public record RequirementPlanningRequest(
        ProjectId projectId,
        String projectCode,
        String projectName,
        String projectPath,
        String projectSummary,
        String title,
        String rawText,
        String acceptanceText,
        String clarificationText) {
}
