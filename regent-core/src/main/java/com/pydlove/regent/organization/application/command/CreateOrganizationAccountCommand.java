package com.pydlove.regent.organization.application.command;

import com.pydlove.regent.organization.domain.model.OrganizationRole;

public record CreateOrganizationAccountCommand(
        String email,
        String displayName,
        OrganizationRole role) {
}
