package com.pydlove.regent.organization.interfaces.dto;

public record CreateOrganizationRequest(
        String code,
        String name,
        String description) {
}
