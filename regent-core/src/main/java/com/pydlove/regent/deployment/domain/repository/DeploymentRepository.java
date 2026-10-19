package com.pydlove.regent.deployment.domain.repository;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;
import java.util.Optional;

/** 部署配置仓储。 */
public interface DeploymentRepository {

    Optional<Deployment> findById(DeploymentId id);

    Optional<Deployment> findByIdForUpdate(DeploymentId id);

    List<Deployment> findAll(ProjectId projectId);

    boolean existsByName(ProjectId projectId, String name, DeploymentId excludingId);

    void save(Deployment deployment);

    void delete(DeploymentId id);
}
