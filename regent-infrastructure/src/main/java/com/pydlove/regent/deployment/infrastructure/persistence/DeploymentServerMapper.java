package com.pydlove.regent.deployment.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 可复用部署服务器 MyBatis 映射。 */
public interface DeploymentServerMapper {

    DeploymentServerRow selectById(@Param("id") long id);

    DeploymentServerRow selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);

    DeploymentServerRow selectByIdForUpdate(@Param("id") long id);

    List<DeploymentServerRow> selectAll(@Param("projectId") Long projectId,
                                        @Param("ownerId") Long ownerId);

    int countByName(@Param("projectId") long projectId,
                    @Param("name") String name,
                    @Param("excludingId") Long excludingId);

    int insert(DeploymentServerData data);

    int update(DeploymentServerData data);

    int softDelete(@Param("id") long id,
                   @Param("updatedAt") LocalDateTime updatedAt);
}
