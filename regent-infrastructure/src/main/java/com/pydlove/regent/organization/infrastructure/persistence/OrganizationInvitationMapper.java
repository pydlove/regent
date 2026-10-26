package com.pydlove.regent.organization.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrganizationInvitationMapper {

    int insert(OrganizationInvitationData data);

    int update(OrganizationInvitationData data);

    OrganizationInvitationData selectByTokenHash(@Param("tokenHash") String tokenHash);

    OrganizationInvitationData selectPending(@Param("organizationId") long organizationId,
                                             @Param("email") String email);

    List<OrganizationInvitationData> selectByOrganization(
            @Param("organizationId") long organizationId);
}
