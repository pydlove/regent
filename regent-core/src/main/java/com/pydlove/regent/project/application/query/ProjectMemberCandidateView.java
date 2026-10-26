package com.pydlove.regent.project.application.query;

import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;

import java.util.List;

public record ProjectMemberCandidateView(
        User user,
        List<OrganizationRole> organizationRoles,
        List<ProjectMemberRole> projectRoles) {
}
