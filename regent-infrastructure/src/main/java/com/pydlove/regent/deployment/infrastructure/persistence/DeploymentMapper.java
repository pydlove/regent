package com.pydlove.regent.deployment.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 部署配置 MyBatis 映射。 */
public interface DeploymentMapper {

    DeploymentRow selectById(@Param("id") long id);

    DeploymentRow selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);

    DeploymentRow selectByIdForUpdate(@Param("id") long id);

    List<DeploymentRow> selectAll(@Param("projectId") Long projectId,
                                  @Param("ownerId") Long ownerId);

    List<DeploymentProjectRow> selectProjectLinks(@Param("deploymentIds") List<Long> deploymentIds);

    int countByNameInProjects(@Param("projectIds") List<Long> projectIds,
                    @Param("name") String name,
                    @Param("excludingId") Long excludingId);

    void deleteProjectLinks(@Param("deploymentId") long deploymentId);

    int insertProjectLinks(@Param("deploymentId") long deploymentId,
                           @Param("projectIds") List<Long> projectIds);

    int countByServerId(@Param("serverId") long serverId);

    List<Long> selectIdsByServerId(@Param("serverId") long serverId);

    int insert(DeploymentData data);

    int update(DeploymentData data);

    int softDelete(@Param("id") long id,
                   @Param("updatedAt") LocalDateTime updatedAt);
}
