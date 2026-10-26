package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 项目成员的一条角色分配。同一用户可以拥有多个角色。 */
public record ProjectMember(
        Long id,
        ProjectId projectId,
        UserId userId,
        ProjectMemberRole role,
        Instant createdAt) {

    public ProjectMember {
        if (projectId == null || userId == null || role == null) {
            throw new DomainException("项目成员关键字段不能为空");
        }
        createdAt = createdAt == null ? Instant.now().truncatedTo(ChronoUnit.MILLIS) : createdAt;
    }

    public static ProjectMember assign(ProjectId projectId,
                                       UserId userId,
                                       ProjectMemberRole role) {
        return new ProjectMember(null, projectId, userId, role,
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    public ProjectMember withId(long assigned) {
        return new ProjectMember(assigned, projectId, userId, role, createdAt);
    }
}
