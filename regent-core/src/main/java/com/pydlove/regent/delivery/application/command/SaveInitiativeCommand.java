package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.project.domain.model.ProjectId;

public record SaveInitiativeCommand(
        ProjectId projectId,
        ProductVersionId versionId,
        String title,
        String businessGoal,
        String scopeText,
        String outOfScopeText,
        String successMetrics,
        String stakeholders,
        String targetVersion,
        String riskText,
        String meetingMinutes) {

    public SaveInitiativeCommand(ProjectId projectId,
                                 String title,
                                 String businessGoal,
                                 String scopeText,
                                 String outOfScopeText,
                                 String successMetrics) {
        this(projectId, null, title, businessGoal, scopeText, outOfScopeText, successMetrics,
                null, null, null, null);
    }
}
