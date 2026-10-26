package com.pydlove.regent.organization.application.query;

import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.project.application.query.ProjectMemberAssignmentView;

import java.util.List;

public record OrganizationMemberView(
        User user,
        List<OrganizationRole> roles,
        List<ProjectMemberAssignmentView> projects) {
}
