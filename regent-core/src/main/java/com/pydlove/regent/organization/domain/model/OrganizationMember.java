package com.pydlove.regent.organization.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record OrganizationMember(
        long id,
        OrganizationId organizationId,
        UserId userId,
        OrganizationRole role,
        Instant joinedAt) {

    public OrganizationMember {
        if (organizationId == null || userId == null || role == null) {
            throw new DomainException("组织成员关键字段不能为空");
        }
        joinedAt = joinedAt == null
                ? Instant.now().truncatedTo(ChronoUnit.MILLIS)
                : joinedAt.truncatedTo(ChronoUnit.MILLIS);
    }

    public static OrganizationMember assign(OrganizationId organizationId,
                                            UserId userId,
                                            OrganizationRole role) {
        return new OrganizationMember(0, organizationId, userId, role, Instant.now());
    }
}
