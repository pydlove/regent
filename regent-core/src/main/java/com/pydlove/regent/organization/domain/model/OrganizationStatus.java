package com.pydlove.regent.organization.domain.model;

public enum OrganizationStatus {
    ACTIVE,
    DISABLED;

    public static OrganizationStatus parse(String value) {
        if (value == null || value.isBlank()) return ACTIVE;
        return valueOf(value);
    }
}
