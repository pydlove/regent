package com.pydlove.regent.organization.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrganizationAuditMapper {

    int insert(OrganizationAuditData data);

    List<OrganizationAuditData> selectRecent(@Param("organizationId") long organizationId,
                                             @Param("limit") int limit);
}
