package com.pydlove.regent.organization.interfaces.dto;

import com.pydlove.regent.organization.domain.model.OrganizationInvitation;

import java.time.Instant;

public record OrganizationInvitationResponse(
        long id,
        String email,
        String role,
        String status,
        Instant expiresAt,
        Instant acceptedAt,
        Instant createdAt) {

    public static OrganizationInvitationResponse from(OrganizationInvitation invitation) {
        return new OrganizationInvitationResponse(
                invitation.getId(),
                invitation.getEmail().value(),
                invitation.getRole().name(),
                invitation.getStatus().name(),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                invitation.getCreatedAt());
    }
}
