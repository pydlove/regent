package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;

/** 根据产品想法生成结构化需求草稿的上下文。 */
public record RequirementIdeaRequest(
        ProjectId projectId,
        String projectCode,
        String projectName,
        String projectPath,
        String projectSummary,
        String idea) {
}
