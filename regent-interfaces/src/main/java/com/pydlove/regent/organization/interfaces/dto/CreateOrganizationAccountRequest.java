package com.pydlove.regent.organization.interfaces.dto;

public record CreateOrganizationAccountRequest(
        String email,
        String displayName,
        String role) {
}
