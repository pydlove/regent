package com.pydlove.regent.organization.application.command;

public record AcceptOrganizationInvitationCommand(
        String displayName,
        String password) {
}
