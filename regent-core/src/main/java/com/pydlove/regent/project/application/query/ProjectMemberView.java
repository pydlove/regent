package com.pydlove.regent.project.application.query;

import com.pydlove.regent.project.domain.model.ProjectMember;

public record ProjectMemberView(
        ProjectMember member,
        String username,
        String displayName,
        String email,
        Long avatarVersion) {
}
