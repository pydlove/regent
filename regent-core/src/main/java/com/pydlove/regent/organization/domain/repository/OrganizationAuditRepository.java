package com.pydlove.regent.organization.domain.repository;

import com.pydlove.regent.organization.domain.model.OrganizationAuditEntry;
import com.pydlove.regent.organization.domain.model.OrganizationId;

import java.util.List;

public interface OrganizationAuditRepository {

    void save(OrganizationAuditEntry entry);

    List<OrganizationAuditEntry> findRecent(OrganizationId organizationId, int limit);
}
