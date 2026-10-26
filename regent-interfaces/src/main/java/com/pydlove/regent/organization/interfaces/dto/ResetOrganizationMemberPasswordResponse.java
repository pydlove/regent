package com.pydlove.regent.organization.interfaces.dto;

public record ResetOrganizationMemberPasswordResponse(
        String initialPassword,
        boolean mustChangePassword) {
}
