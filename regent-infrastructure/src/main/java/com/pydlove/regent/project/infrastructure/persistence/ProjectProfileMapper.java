package com.pydlove.regent.project.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 项目画像查询的 MyBatis 映射接口。SQL 在 {@code mapper/ProjectProfileMapper.xml}。 */
public interface ProjectProfileMapper {

    ProjectProfileRow selectById(@Param("id") long id);

    ProjectProfileRow selectByCode(@Param("code") String code);

    List<ProjectProfileRow> selectAll();
}
