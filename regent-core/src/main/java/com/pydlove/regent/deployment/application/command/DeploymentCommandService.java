package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentScriptIssue;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.domain.service.DeploymentScriptValidator;
import com.pydlove.regent.deployment.domain.repository.DeploymentRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentRunRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentServerRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.List;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;

/** 部署配置的写侧用例。执行快照由执行服务负责。 */
public class DeploymentCommandService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentRunRepository runRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final DeploymentServerRepository serverRepository;
    private final TransactionRunner transactionRunner;

    public DeploymentCommandService(DeploymentRepository deploymentRepository,
                                    DeploymentRunRepository runRepository,
                                    ProjectProfileRepository projectProfileRepository,
                                    DeploymentServerRepository serverRepository,
                                    TransactionRunner transactionRunner) {
        this.deploymentRepository = deploymentRepository;
        this.runRepository = runRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.serverRepository = serverRepository;
        this.transactionRunner = transactionRunner;
    }

    public Deployment create(SaveDeploymentCommand command) {
        return transactionRunner.inTransaction(() -> {
            DeploymentScriptValidator.requireValid(command.scriptContent());
            List<ProjectId> projectIds = requireProjects(command.projectIds());
            requireProjectsExist(projectIds);
            requireServer(projectIds, command.serverId());
            Deployment deployment = Deployment.create(
                    projectIds,
                    command.serverId(),
                    command.name(),
                    command.remoteDirectory(),
                    command.scriptFilename(),
                    command.scriptContent(),
                    command.executionMode(),
                    command.projectScriptPath());
            requireNameAvailable(projectIds, deployment.getName(), null);
            deploymentRepository.save(deployment);
            return deployment;
        });
    }

    public Deployment update(DeploymentId id, SaveDeploymentCommand command) {
        return transactionRunner.inTransaction(() -> {
            DeploymentScriptValidator.requireValid(command.scriptContent());
            Deployment deployment = deploymentRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("部署不存在：" + id.value()));
            List<ProjectId> projectIds = requireProjects(command.projectIds());
            requireProjectsExist(projectIds);
            requireServer(projectIds, command.serverId());
            String nextName = command.name() == null ? deployment.getName() : command.name().trim();
            requireNameAvailable(projectIds, nextName, id);
            deployment.update(
                    projectIds,
                    nextName,
                    command.serverId(),
                    command.remoteDirectory(),
                    command.scriptFilename(),
                    command.scriptContent(),
                    command.executionMode(),
                    command.projectScriptPath());
            deploymentRepository.save(deployment);
            return deployment;
        });
    }

    public void delete(DeploymentId id) {
        transactionRunner.inTransaction(() -> {
            deploymentRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("部署不存在：" + id.value()));
            if (runRepository.existsActiveByDeployment(id)) {
                throw new DomainException("部署正在执行，不能删除；请等本次执行结束后再试");
            }
            deploymentRepository.delete(id);
            return null;
        });
    }

    /** 前端上传脚本后先调用，返回逐行问题而不是等保存失败才提示。 */
    public List<DeploymentScriptIssue> validateScript(String scriptContent) {
        return DeploymentScriptValidator.validate(scriptContent);
    }

    private void requireProjectsExist(Collection<ProjectId> projectIds) {
        for (ProjectId projectId : projectIds) {
            if (projectProfileRepository.findById(projectId).isEmpty()) {
                throw new EntityNotFoundException("项目不存在：" + projectId);
            }
        }
    }

    private void requireNameAvailable(Collection<ProjectId> projectIds,
                                      String name,
                                      DeploymentId excludingId) {
        if (deploymentRepository.existsByName(projectIds, name, excludingId)) {
            throw new DomainException("所选项目中已存在同名部署：" + name);
        }
    }

    private void requireServer(Collection<ProjectId> projectIds, DeploymentServerId serverId) {
        if (serverId == null) {
            throw new DomainException("请选择部署服务器");
        }
        DeploymentServer server = serverRepository.findByIdForUpdate(serverId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "部署服务器不存在：" + serverId.value()));
        for (ProjectId projectId : projectIds) {
            if (!server.belongsTo(projectId)) {
                throw new DomainException("所选服务器未归属项目：" + projectId.value());
            }
        }
    }

    private static List<ProjectId> requireProjects(List<ProjectId> projectIds) {
        if (projectIds == null || projectIds.isEmpty() || projectIds.stream().anyMatch(Objects::isNull)) {
            throw new DomainException("请至少选择一个部署归属项目");
        }
        return List.copyOf(new LinkedHashSet<>(projectIds));
    }
}
