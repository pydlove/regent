package com.pydlove.regent.organization.domain.repository;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.model.OrganizationInvitation;

import java.util.List;
import java.util.Optional;

public interface OrganizationInvitationRepository {

    OrganizationInvitation save(OrganizationInvitation invitation);

    void update(OrganizationInvitation invitation);

    Optional<OrganizationInvitation> findByTokenHash(String tokenHash);

    Optional<OrganizationInvitation> findPending(OrganizationId organizationId, Email email);

    List<OrganizationInvitation> findByOrganization(OrganizationId organizationId);
}
