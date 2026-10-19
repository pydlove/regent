package com.pydlove.regent.deployment.application.query;

import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.domain.port.DeploymentServerCredentialStore;
import com.pydlove.regent.deployment.domain.repository.DeploymentRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentServerRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;

/** 可复用部署服务器的读侧用例。 */
public class DeploymentServerQueryService {

    private final DeploymentServerRepository serverRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentServerCredentialStore credentialStore;

    public DeploymentServerQueryService(DeploymentServerRepository serverRepository,
                                        DeploymentRepository deploymentRepository,
                                        DeploymentServerCredentialStore credentialStore) {
        this.serverRepository = serverRepository;
        this.deploymentRepository = deploymentRepository;
        this.credentialStore = credentialStore;
    }

    public List<DeploymentServerOverview> list(ProjectId projectId) {
        return list(projectId, null);
    }

    public List<DeploymentServerOverview> list(ProjectId projectId, UserId ownerId) {
        return serverRepository.findAll(projectId, ownerId).stream()
                .map(server -> new DeploymentServerOverview(
                        server,
                        credentialStore.exists(server.getId()),
                        deploymentRepository.countByServerId(server.getId())))
                .toList();
    }

    public DeploymentServerOverview get(DeploymentServerId id) {
        return get(id, null);
    }

    public DeploymentServerOverview get(DeploymentServerId id, UserId ownerId) {
        var server = (ownerId == null
                ? serverRepository.findById(id)
                : serverRepository.findByIdOwned(id, ownerId))
                .orElseThrow(() -> new EntityNotFoundException("部署服务器不存在：" + id.value()));
        return new DeploymentServerOverview(
                server,
                credentialStore.exists(id),
                deploymentRepository.countByServerId(id));
    }
}
