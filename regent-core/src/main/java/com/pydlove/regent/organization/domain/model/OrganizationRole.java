package com.pydlove.regent.organization.domain.model;

public enum OrganizationRole {
    ORG_OWNER,
    ORG_ADMIN,
    PROJECT_MANAGER,
    ORG_MEMBER;

    public boolean canManageOrganization() {
        return this == ORG_OWNER || this == ORG_ADMIN;
    }

    public boolean canManageProjects() {
        return canManageOrganization() || this == PROJECT_MANAGER;
    }
}
