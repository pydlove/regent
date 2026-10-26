package com.pydlove.regent.organization.application.command;

import com.pydlove.regent.organization.domain.model.OrganizationRole;

public record InviteOrganizationMemberCommand(
        String email,
        OrganizationRole role) {
}
