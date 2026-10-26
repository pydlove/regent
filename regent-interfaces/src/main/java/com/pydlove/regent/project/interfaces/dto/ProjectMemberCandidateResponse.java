package com.pydlove.regent.project.interfaces.dto;

import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.project.application.query.ProjectMemberCandidateView;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;

import java.util.List;

public record ProjectMemberCandidateResponse(
        long userId,
        String username,
        String displayName,
        String email,
        List<String> organizationRoles,
        List<String> projectRoles) {

    public static ProjectMemberCandidateResponse from(ProjectMemberCandidateView view) {
        return new ProjectMemberCandidateResponse(
                view.user().getId().value(),
                view.user().getUsername().value(),
                view.user().getDisplayName().value(),
                view.user().getEmail() == null ? null : view.user().getEmail().value(),
                view.organizationRoles().stream().map(OrganizationRole::name).toList(),
                view.projectRoles().stream().map(ProjectMemberRole::name).toList());
    }
}
