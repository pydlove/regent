package com.pydlove.regent.deployment.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 部署执行记录 MyBatis 映射。 */
public interface DeploymentRunMapper {

    DeploymentRunRow selectById(@Param("id") long id);

    DeploymentRunRow selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);

    List<DeploymentRunRow> selectByDeployment(@Param("deploymentId") long deploymentId,
                                              @Param("limit") int limit);

    List<DeploymentRunRow> selectLatestByDeploymentIds(
            @Param("deploymentIds") List<Long> deploymentIds);

    List<DeploymentRunRow> selectActive();

    int countActiveByDeployment(@Param("deploymentId") long deploymentId);

    int insert(DeploymentRunData data);

    int update(DeploymentRunData data);
}
