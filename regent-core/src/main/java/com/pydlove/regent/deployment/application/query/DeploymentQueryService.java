package com.pydlove.regent.deployment.application.query;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.port.DeploymentServerCredentialStore;
import com.pydlove.regent.deployment.domain.repository.DeploymentRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentRunRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentServerRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 部署配置读侧。 */
public class DeploymentQueryService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentRunRepository runRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final DeploymentServerRepository serverRepository;
    private final DeploymentServerCredentialStore credentialStore;

    public DeploymentQueryService(DeploymentRepository deploymentRepository,
                                  DeploymentRunRepository runRepository,
                                  ProjectProfileRepository projectProfileRepository,
                                  DeploymentServerRepository serverRepository,
                                  DeploymentServerCredentialStore credentialStore) {
        this.deploymentRepository = deploymentRepository;
        this.runRepository = runRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.serverRepository = serverRepository;
        this.credentialStore = credentialStore;
    }

    public List<DeploymentOverview> list(ProjectId projectId) {
        return list(projectId, null);
    }

    public List<DeploymentOverview> list(ProjectId projectId, UserId ownerId) {
        List<Deployment> deployments = deploymentRepository.findAll(projectId, ownerId);
        if (deployments.isEmpty()) {
            return List.of();
        }
        Map<Long, String> projectNames = (ownerId == null
                ? projectProfileRepository.findAll()
                : projectProfileRepository.findAll(ownerId)).stream()
                .collect(Collectors.toMap(
                        profile -> profile.id().value(),
                        ProjectProfile::name,
                        (left, right) -> left));
        Map<Long, DeploymentServer> servers = serverRepository.findAll(projectId, ownerId).stream()
                .collect(Collectors.toMap(
                        server -> server.getId().value(),
                        Function.identity(),
                        (left, right) -> left));
        Map<DeploymentId, DeploymentRun> latest = runRepository
                .findLatestByDeploymentIds(deployments.stream().map(Deployment::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        DeploymentRun::getDeploymentId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        return deployments.stream()
                .map(deployment -> {
                    DeploymentServer server = requireServer(
                            servers.get(deployment.getServerId().value()), deployment);
                    return new DeploymentOverview(
                            deployment,
                            server,
                            projectNames.getOrDefault(
                                    deployment.getProjectId().value(),
                                    "项目 #" + deployment.getProjectId().value()),
                            latest.get(deployment.getId()),
                            credentialStore.exists(server.getId()));
                })
                .toList();
    }

    public DeploymentOverview get(DeploymentId id) {
        return get(id, null);
    }

    public DeploymentOverview get(DeploymentId id, UserId ownerId) {
        Deployment deployment = (ownerId == null
                ? deploymentRepository.findById(id)
                : deploymentRepository.findByIdOwned(id, ownerId))
                .orElseThrow(() -> new EntityNotFoundException("部署不存在：" + id.value()));
        DeploymentRun latest = runRepository.findLatestByDeploymentIds(List.of(id))
                .stream()
                .findFirst()
                .orElse(null);
        String projectName = (ownerId == null
                ? projectProfileRepository.findById(deployment.getProjectId())
                : projectProfileRepository.findById(deployment.getProjectId(), ownerId))
                .map(ProjectProfile::name)
                .orElse("项目 #" + deployment.getProjectId().value());
        DeploymentServer server = (ownerId == null
                ? serverRepository.findById(deployment.getServerId())
                : serverRepository.findByIdOwned(deployment.getServerId(), ownerId))
                .orElseThrow(() -> new EntityNotFoundException(
                        "部署服务器不存在：" + deployment.getServerId().value()));
        return new DeploymentOverview(
                deployment, server, projectName, latest, credentialStore.exists(server.getId()));
    }

    private static DeploymentServer requireServer(DeploymentServer server,
                                                  Deployment deployment) {
        if (server == null) {
            throw new EntityNotFoundException(
                    "部署服务器不存在：" + deployment.getServerId().value());
        }
        return server;
    }
}
