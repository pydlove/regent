package com.pydlove.regent.organization.application.query;

import com.pydlove.regent.organization.domain.model.OrganizationInvitation;

public record CreatedOrganizationInvitation(
        OrganizationInvitation invitation,
        String rawToken,
        String invitationUrl,
        String organizationName,
        boolean emailSent) {

    public CreatedOrganizationInvitation withEmailSent(boolean sent) {
        return new CreatedOrganizationInvitation(
                invitation, rawToken, invitationUrl, organizationName, sent);
    }
}
