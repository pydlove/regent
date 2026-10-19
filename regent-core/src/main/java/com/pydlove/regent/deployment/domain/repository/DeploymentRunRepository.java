package com.pydlove.regent.deployment.domain.repository;

import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.identity.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/** 部署执行记录仓储。 */
public interface DeploymentRunRepository {

    Optional<DeploymentRun> findById(DeploymentRunId id);

    default Optional<DeploymentRun> findByIdOwned(DeploymentRunId id, UserId ownerId) {
        return findById(id);
    }

    List<DeploymentRun> findByDeployment(DeploymentId deploymentId, int limit);

    List<DeploymentRun> findLatestByDeploymentIds(List<DeploymentId> deploymentIds);

    List<DeploymentRun> findActive();

    boolean existsActiveByDeployment(DeploymentId deploymentId);

    void save(DeploymentRun run);
}
