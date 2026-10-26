package com.pydlove.regent.organization.interfaces.dto;

import com.pydlove.regent.organization.application.query.CreatedOrganizationInvitation;

public record CreatedOrganizationInvitationResponse(
        OrganizationInvitationResponse invitation,
        String token,
        String invitationUrl,
        boolean emailSent) {

    public static CreatedOrganizationInvitationResponse from(
            CreatedOrganizationInvitation created) {
        return new CreatedOrganizationInvitationResponse(
                OrganizationInvitationResponse.from(created.invitation()),
                created.rawToken(),
                created.invitationUrl(),
                created.emailSent());
    }
}
