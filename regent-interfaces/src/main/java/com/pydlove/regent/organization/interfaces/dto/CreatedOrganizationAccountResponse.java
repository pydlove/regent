package com.pydlove.regent.organization.interfaces.dto;

import com.pydlove.regent.organization.application.query.CreatedOrganizationAccount;
import com.pydlove.regent.organization.domain.model.OrganizationRole;

public record CreatedOrganizationAccountResponse(
        long userId,
        String username,
        String displayName,
        String email,
        String role,
        String initialPassword,
        boolean mustChangePassword) {

    public static CreatedOrganizationAccountResponse from(
            CreatedOrganizationAccount created,
            OrganizationRole role) {
        return new CreatedOrganizationAccountResponse(
                created.user().getId().value(),
                created.user().getUsername().value(),
                created.user().getDisplayName().value(),
                created.user().getEmail().value(),
                role.name(),
                created.initialPassword(),
                created.user().mustChangePassword());
    }
}
