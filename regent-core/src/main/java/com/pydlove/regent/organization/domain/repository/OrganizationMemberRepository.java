package com.pydlove.regent.organization.domain.repository;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.model.OrganizationMember;
import com.pydlove.regent.organization.domain.model.OrganizationRole;

import java.util.List;

public interface OrganizationMemberRepository {

    void save(OrganizationMember member);

    List<OrganizationMember> findByOrganization(OrganizationId organizationId);

    List<OrganizationMember> findByUser(UserId userId);

    List<OrganizationRole> findRoles(OrganizationId organizationId, UserId userId);

    boolean exists(OrganizationId organizationId, UserId userId, OrganizationRole role);

    void deleteRole(OrganizationId organizationId, UserId userId, OrganizationRole role);

    void deleteMember(OrganizationId organizationId, UserId userId);

    int countDistinctUsers(OrganizationId organizationId);

    int countByRole(OrganizationId organizationId, OrganizationRole role);
}
