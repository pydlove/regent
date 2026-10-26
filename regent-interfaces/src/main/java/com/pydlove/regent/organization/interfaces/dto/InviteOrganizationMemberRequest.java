package com.pydlove.regent.organization.interfaces.dto;

public record InviteOrganizationMemberRequest(
        String email,
        String role) {
}
