package com.pydlove.regent.organization.application.query;

import com.pydlove.regent.organization.domain.model.Organization;
import com.pydlove.regent.organization.domain.model.OrganizationRole;

import java.util.List;

public record OrganizationView(
        Organization organization,
        List<OrganizationRole> currentUserRoles,
        int memberCount,
        long projectCount) {
}
