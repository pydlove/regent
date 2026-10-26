package com.pydlove.regent.project.application.query;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;

import java.util.List;

public record ProjectMemberAssignmentView(
        ProjectId projectId,
        String projectName,
        List<ProjectMemberRole> roles) {
}
