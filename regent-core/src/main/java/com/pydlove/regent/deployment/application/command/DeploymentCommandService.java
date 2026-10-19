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
            ProjectId projectId = requireProject(command.projectId());
            requireProjectExists(projectId);
            requireServer(projectId, command.serverId());
            Deployment deployment = Deployment.create(
                    projectId,
                    command.serverId(),
                    command.name(),
                    command.remoteDirectory(),
                    command.scriptFilename(),
                    command.scriptContent(),
                    command.executionMode(),
                    command.projectScriptPath());
            requireNameAvailable(projectId, deployment.getName(), null);
            deploymentRepository.save(deployment);
            return deployment;
        });
    }

    public Deployment update(DeploymentId id, SaveDeploymentCommand command) {
        return transactionRunner.inTransaction(() -> {
            DeploymentScriptValidator.requireValid(command.scriptContent());
            Deployment deployment = deploymentRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("部署不存在：" + id.value()));
            if (!deployment.getProjectId().equals(requireProject(command.projectId()))) {
                throw new DomainException("部署归属项目创建后不能修改");
            }
            requireServer(deployment.getProjectId(), command.serverId());
            String nextName = command.name() == null ? deployment.getName() : command.name().trim();
            requireNameAvailable(deployment.getProjectId(), nextName, id);
            deployment.update(
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

    private void requireProjectExists(ProjectId projectId) {
        if (projectProfileRepository.findById(projectId).isEmpty()) {
            throw new EntityNotFoundException("项目不存在：" + projectId);
        }
    }

    private void requireNameAvailable(ProjectId projectId, String name, DeploymentId excludingId) {
        if (deploymentRepository.existsByName(projectId, name, excludingId)) {
            throw new DomainException("该项目已存在同名部署：" + name);
        }
    }

    private void requireServer(ProjectId projectId, DeploymentServerId serverId) {
        if (serverId == null) {
            throw new DomainException("请选择部署服务器");
        }
        DeploymentServer server = serverRepository.findByIdForUpdate(serverId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "部署服务器不存在：" + serverId.value()));
        if (!server.getProjectId().equals(projectId)) {
            throw new DomainException("部署与服务器必须属于同一项目");
        }
    }

    private static ProjectId requireProject(ProjectId projectId) {
        if (projectId == null) {
            throw new DomainException("请选择部署归属项目");
        }
        return projectId;
    }
}
