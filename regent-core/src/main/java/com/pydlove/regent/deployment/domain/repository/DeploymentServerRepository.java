package com.pydlove.regent.deployment.domain.repository;

import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;
import java.util.Optional;

/** 可复用部署服务器仓储。 */
public interface DeploymentServerRepository {

    Optional<DeploymentServer> findById(DeploymentServerId id);

    default Optional<DeploymentServer> findByIdOwned(DeploymentServerId id, UserId ownerId) {
        return findById(id);
    }

    Optional<DeploymentServer> findByIdForUpdate(DeploymentServerId id);

    List<DeploymentServer> findAll(ProjectId projectId);

    default List<DeploymentServer> findAll(ProjectId projectId, UserId ownerId) {
        return findAll(projectId);
    }

    boolean existsByName(ProjectId projectId, String name, DeploymentServerId excludingId);

    void save(DeploymentServer server);

    void delete(DeploymentServerId id);
}
