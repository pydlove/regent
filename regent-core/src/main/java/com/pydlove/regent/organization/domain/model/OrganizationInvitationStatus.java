package com.pydlove.regent.organization.domain.model;

public enum OrganizationInvitationStatus {
    PENDING,
    ACCEPTED,
    REVOKED,
    EXPIRED;

    public static OrganizationInvitationStatus parse(String value) {
        return value == null || value.isBlank() ? PENDING : valueOf(value);
    }
}
