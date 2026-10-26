package com.pydlove.regent.organization.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrganizationMapper {

    int insert(OrganizationData data);

    int update(OrganizationData data);

    OrganizationData selectById(@Param("id") long id);

    OrganizationData selectByCode(@Param("code") String code);

    List<OrganizationData> selectByIds(@Param("ids") List<Long> ids);
}
