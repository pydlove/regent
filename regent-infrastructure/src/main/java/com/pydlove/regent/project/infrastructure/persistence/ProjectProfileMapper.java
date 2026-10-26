package com.pydlove.regent.project.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 项目画像查询的 MyBatis 映射接口。SQL 在 {@code mapper/ProjectProfileMapper.xml}。 */
public interface ProjectProfileMapper {

    ProjectProfileRow selectById(@Param("id") long id);

    ProjectProfileRow selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);

    ProjectProfileRow selectByCode(@Param("code") String code);

    List<ProjectProfileRow> selectAll();

    List<ProjectProfileRow> selectAllOwned(@Param("ownerId") long ownerId,
                                           @Param("organizationId") Long organizationId);

    List<ProjectProfileRow> selectByOrganization(@Param("organizationId") long organizationId);

    long countByOrganization(@Param("organizationId") long organizationId);
}
