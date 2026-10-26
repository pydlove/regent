package com.pydlove.regent.deployment.domain.repository;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;
import java.util.Optional;

/** 部署配置仓储。 */
public interface DeploymentRepository {

    Optional<Deployment> findById(DeploymentId id);

    default Optional<Deployment> findByIdOwned(DeploymentId id, UserId ownerId) {
        return findById(id);
    }

    Optional<Deployment> findByIdForUpdate(DeploymentId id);

    List<Deployment> findAll(ProjectId projectId);

    default List<Deployment> findAll(ProjectId projectId, UserId ownerId) {
        return findAll(projectId);
    }

    boolean existsByName(java.util.Collection<ProjectId> projectIds, String name, DeploymentId excludingId);

    List<DeploymentId> findIdsByServerId(DeploymentServerId serverId);

    int countByServerId(DeploymentServerId serverId);

    void save(Deployment deployment);

    void delete(DeploymentId id);
}
