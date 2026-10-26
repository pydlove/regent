package com.pydlove.regent.organization.interfaces.dto;

import com.pydlove.regent.organization.application.query.AcceptedOrganizationInvitation;

public record AcceptedOrganizationInvitationResponse(
        long organizationId,
        String organizationName,
        long userId,
        String username,
        String displayName) {

    public static AcceptedOrganizationInvitationResponse from(
            AcceptedOrganizationInvitation accepted) {
        return new AcceptedOrganizationInvitationResponse(
                accepted.organization().getId().value(),
                accepted.organization().getName(),
                accepted.user().getId().value(),
                accepted.user().getUsername().value(),
                accepted.user().getDisplayName().value());
    }
}
