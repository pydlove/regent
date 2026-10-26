package com.pydlove.regent.organization.application.query;

import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.organization.domain.model.Organization;

public record AcceptedOrganizationInvitation(
        Organization organization,
        User user) {
}
