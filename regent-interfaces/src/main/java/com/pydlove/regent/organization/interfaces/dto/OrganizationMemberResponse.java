package com.pydlove.regent.organization.interfaces.dto;

import com.pydlove.regent.organization.application.query.OrganizationMemberView;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;

import java.util.List;

public record OrganizationMemberResponse(
        long userId,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        List<String> roles,
        List<ProjectAssignment> projects) {

    public static OrganizationMemberResponse from(OrganizationMemberView view) {
        return new OrganizationMemberResponse(
                view.user().getId().value(),
                view.user().getUsername().value(),
                view.user().getDisplayName().value(),
                view.user().getEmail() == null ? null : view.user().getEmail().value(),
                avatarUrl(view),
                view.roles().stream().map(OrganizationRole::name).toList(),
                view.projects().stream()
                        .map(project -> new ProjectAssignment(
                                project.projectId().value(),
                                project.projectName(),
                                project.roles().stream().map(ProjectMemberRole::name).toList()))
                        .toList());
    }

    private static String avatarUrl(OrganizationMemberView view) {
        if (view.user().getAvatar() == null) return null;
        return "/api/profile/avatar/" + view.user().getId().value()
                + "?v=" + view.user().getAvatar().updatedAt().toEpochMilli();
    }

    public record ProjectAssignment(
            long projectId,
            String projectName,
            List<String> roles) {
    }
}
