package com.pydlove.regent.organization.domain.repository;

import com.pydlove.regent.organization.domain.model.Organization;
import com.pydlove.regent.organization.domain.model.OrganizationId;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {

    Organization save(Organization organization);

    void update(Organization organization);

    Optional<Organization> findById(OrganizationId id);

    Optional<Organization> findByCode(String code);

    List<Organization> findByIds(List<OrganizationId> ids);
}
