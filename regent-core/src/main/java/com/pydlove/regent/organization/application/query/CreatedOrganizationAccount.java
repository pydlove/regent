package com.pydlove.regent.organization.application.query;

import com.pydlove.regent.identity.domain.model.User;

public record CreatedOrganizationAccount(
        User user,
        String initialPassword) {
}
