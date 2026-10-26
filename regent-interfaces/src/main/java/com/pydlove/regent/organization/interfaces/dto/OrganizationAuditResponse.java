package com.pydlove.regent.organization.interfaces.dto;

import com.pydlove.regent.organization.domain.model.OrganizationAuditEntry;

import java.time.Instant;

public record OrganizationAuditResponse(
        long id,
        long actorId,
        String action,
        String targetType,
        Long targetId,
        String detail,
        Instant createdAt) {

    public static OrganizationAuditResponse from(OrganizationAuditEntry entry) {
        return new OrganizationAuditResponse(
                entry.id() == null ? 0 : entry.id(),
                entry.actorId().value(),
                entry.action(),
                entry.targetType(),
                entry.targetId(),
                entry.detailJson(),
                entry.createdAt());
    }
}
