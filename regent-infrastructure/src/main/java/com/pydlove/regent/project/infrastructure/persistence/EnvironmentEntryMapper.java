package com.pydlove.regent.project.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 环境信息映射接口。SQL 在 {@code mapper/EnvironmentEntryMapper.xml}。 */
public interface EnvironmentEntryMapper {

    EnvironmentRow selectById(@Param("id") long id);

    EnvironmentRow selectByIdOwned(@Param("id") long id, @Param("userId") long userId);

    List<EnvironmentRow> selectAll();

    List<EnvironmentRow> selectAllOwned(@Param("userId") long userId);

    List<EnvironmentRow> selectApplicableTo(@Param("projectId") long projectId);

    List<EnvironmentRow> selectApplicableToOwned(@Param("userId") long userId,
                                                 @Param("projectId") long projectId);

    int countByKey(@Param("projectId") Long projectId,
                   @Param("itemKey") String itemKey,
                   @Param("excludingId") Long excludingId);

    int countByKeyOwned(@Param("userId") long userId,
                        @Param("projectId") Long projectId,
                        @Param("itemKey") String itemKey,
                        @Param("excludingId") Long excludingId);

    int insert(EnvironmentData data);

    int update(EnvironmentData data);

    int softDelete(@Param("id") long id);
}
