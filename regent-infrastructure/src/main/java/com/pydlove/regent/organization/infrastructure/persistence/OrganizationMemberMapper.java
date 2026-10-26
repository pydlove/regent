package com.pydlove.regent.organization.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrganizationMemberMapper {

    int insert(OrganizationMemberData data);

    List<OrganizationMemberData> selectByOrganization(@Param("organizationId") long organizationId);

    List<OrganizationMemberData> selectByUser(@Param("userId") long userId);

    List<String> selectRoles(@Param("organizationId") long organizationId,
                             @Param("userId") long userId);

    int countRole(@Param("organizationId") long organizationId,
                  @Param("userId") long userId,
                  @Param("roleCode") String roleCode);

    int countDistinctUsers(@Param("organizationId") long organizationId);

    int countByRole(@Param("organizationId") long organizationId,
                    @Param("roleCode") String roleCode);

    int deleteRole(@Param("organizationId") long organizationId,
                   @Param("userId") long userId,
                   @Param("roleCode") String roleCode);

    int deleteMember(@Param("organizationId") long organizationId,
                     @Param("userId") long userId);
}
