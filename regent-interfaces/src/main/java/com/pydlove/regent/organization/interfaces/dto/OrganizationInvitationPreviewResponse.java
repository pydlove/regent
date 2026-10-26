package com.pydlove.regent.organization.interfaces.dto;

import com.pydlove.regent.organization.application.query.OrganizationInvitationPreview;

import java.time.Instant;

public record OrganizationInvitationPreviewResponse(
        String organizationName,
        String email,
        String role,
        String status,
        Instant expiresAt,
        boolean accountExists) {

    public static OrganizationInvitationPreviewResponse from(
            OrganizationInvitationPreview preview) {
        return new OrganizationInvitationPreviewResponse(
                preview.organizationName(),
                preview.invitation().getEmail().value(),
                preview.invitation().getRole().name(),
                preview.invitation().getStatus().name(),
                preview.invitation().getExpiresAt(),
                preview.accountExists());
    }
}
