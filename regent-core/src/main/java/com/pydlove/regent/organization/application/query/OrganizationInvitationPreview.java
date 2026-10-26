package com.pydlove.regent.organization.application.query;

import com.pydlove.regent.organization.domain.model.OrganizationInvitation;

public record OrganizationInvitationPreview(
        OrganizationInvitation invitation,
        String organizationName,
        boolean accountExists) {
}
