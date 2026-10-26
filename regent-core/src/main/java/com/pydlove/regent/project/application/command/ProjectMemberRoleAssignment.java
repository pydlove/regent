package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.ProjectMemberRole;

import java.util.List;

/** 一次批量添加中，某个组织成员需要获得的项目角色。 */
public record ProjectMemberRoleAssignment(
        String email,
        List<ProjectMemberRole> roles
) {
    public ProjectMemberRoleAssignment {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
