package com.pydlove.regent.requirement.application.command;

import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementSource;

/** 新建或更新需求草稿。 */
public record SaveRequirementCommand(
        ProjectId projectId,
        InitiativeId initiativeId,
        RequirementSource source,
        String title,
        String rawText,
        String acceptanceText,
        String clarificationText) {

    public SaveRequirementCommand(ProjectId projectId,
                                  String title,
                                  String rawText,
                                  String acceptanceText,
                                  String clarificationText) {
        this(projectId, null, RequirementSource.MANUAL,
                title, rawText, acceptanceText, clarificationText);
    }

    public SaveRequirementCommand(ProjectId projectId,
                                  InitiativeId initiativeId,
                                  String title,
                                  String rawText,
                                  String acceptanceText,
                                  String clarificationText) {
        this(projectId, initiativeId, RequirementSource.MANUAL,
                title, rawText, acceptanceText, clarificationText);
    }
}
