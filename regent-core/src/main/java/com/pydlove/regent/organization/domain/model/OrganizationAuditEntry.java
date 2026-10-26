package com.pydlove.regent.organization.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record OrganizationAuditEntry(
        Long id,
        OrganizationId organizationId,
        UserId actorId,
        String action,
        String targetType,
        Long targetId,
        String detailJson,
        Instant createdAt) {

    public OrganizationAuditEntry {
        if (organizationId == null || actorId == null
                || action == null || action.isBlank()
                || targetType == null || targetType.isBlank()) {
            throw new DomainException("组织审计关键字段不能为空");
        }
        createdAt = createdAt == null
                ? Instant.now().truncatedTo(ChronoUnit.MILLIS)
                : createdAt.truncatedTo(ChronoUnit.MILLIS);
    }

    public static OrganizationAuditEntry record(OrganizationId organizationId,
                                                UserId actorId,
                                                String action,
                                                String targetType,
                                                Long targetId,
                                                String detailJson) {
        return new OrganizationAuditEntry(null, organizationId, actorId, action, targetType,
                targetId, detailJson, Instant.now());
    }
}
