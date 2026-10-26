package com.pydlove.regent.project.interfaces.dto;

import java.util.List;

/** 批量把一组组织成员加入项目，并为每个成员授予各自的多个项目角色。 */
public record ProjectMemberBatchAddRequest(
        List<MemberAssignment> members
) {
    public record MemberAssignment(
            String email,
            List<String> roles
    ) {
        public MemberAssignment {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }
}
