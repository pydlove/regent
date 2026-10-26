package com.pydlove.regent.organization.application.command;

public record UpdateOrganizationCommand(
        String name,
        String description) {
}
