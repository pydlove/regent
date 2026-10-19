package com.pydlove.regent.deployment.application.query;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.port.DeploymentCredentialStore;
import com.pydlove.regent.deployment.domain.repository.DeploymentRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentRunRepository;
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
    private final DeploymentCredentialStore credentialStore;

    public DeploymentQueryService(DeploymentRepository deploymentRepository,
                                  DeploymentRunRepository runRepository,
                                  ProjectProfileRepository projectProfileRepository,
                                  DeploymentCredentialStore credentialStore) {
        this.deploymentRepository = deploymentRepository;
        this.runRepository = runRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.credentialStore = credentialStore;
    }

    public List<DeploymentOverview> list(ProjectId projectId) {
        List<Deployment> deployments = deploymentRepository.findAll(projectId);
        if (deployments.isEmpty()) {
            return List.of();
        }
        Map<Long, String> projectNames = projectProfileRepository.findAll().stream()
                .collect(Collectors.toMap(
                        profile -> profile.id().value(),
                        ProjectProfile::name,
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
                .map(deployment -> new DeploymentOverview(
                        deployment,
                        projectNames.getOrDefault(
                                deployment.getProjectId().value(),
                                "项目 #" + deployment.getProjectId().value()),
                        latest.get(deployment.getId()),
                        credentialStore.exists(deployment.getId())))
                .toList();
    }

    public DeploymentOverview get(DeploymentId id) {
        Deployment deployment = deploymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("部署不存在：" + id.value()));
        DeploymentRun latest = runRepository.findLatestByDeploymentIds(List.of(id))
                .stream()
                .findFirst()
                .orElse(null);
        String projectName = projectProfileRepository.findById(deployment.getProjectId())
                .map(ProjectProfile::name)
                .orElse("项目 #" + deployment.getProjectId().value());
        return new DeploymentOverview(
                deployment, projectName, latest, credentialStore.exists(id));
    }
}
