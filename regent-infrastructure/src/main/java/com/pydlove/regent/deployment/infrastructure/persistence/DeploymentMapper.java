package com.pydlove.regent.deployment.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 部署配置 MyBatis 映射。 */
public interface DeploymentMapper {

    DeploymentRow selectById(@Param("id") long id);

    DeploymentRow selectByIdForUpdate(@Param("id") long id);

    List<DeploymentRow> selectAll(@Param("projectId") Long projectId);

    int countByName(@Param("projectId") long projectId,
                    @Param("name") String name,
                    @Param("excludingId") Long excludingId);

    int insert(DeploymentData data);

    int update(DeploymentData data);

    int softDelete(@Param("id") long id,
                   @Param("updatedAt") LocalDateTime updatedAt);
}
