package com.pydlove.regent.organization.application.command;

public record CreateOrganizationCommand(
        String code,
        String name,
        String description) {
}
