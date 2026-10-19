package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.port.DeploymentCredentialStore;
import com.pydlove.regent.deployment.domain.repository.DeploymentRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentRunRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

/** 部署配置的写侧用例。执行快照由执行服务负责。 */
public class DeploymentCommandService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentRunRepository runRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final DeploymentCredentialStore credentialStore;
    private final TransactionRunner transactionRunner;

    public DeploymentCommandService(DeploymentRepository deploymentRepository,
                                    DeploymentRunRepository runRepository,
                                    ProjectProfileRepository projectProfileRepository,
                                    DeploymentCredentialStore credentialStore,
                                    TransactionRunner transactionRunner) {
        this.deploymentRepository = deploymentRepository;
        this.runRepository = runRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.credentialStore = credentialStore;
        this.transactionRunner = transactionRunner;
    }

    public Deployment create(SaveDeploymentCommand command) {
        return transactionRunner.inTransaction(() -> {
            ProjectId projectId = requireProject(command.projectId());
            requireProjectExists(projectId);
            Deployment deployment = Deployment.create(
                    projectId,
                    command.name(),
                    command.host(),
                    command.port(),
                    command.username(),
                    command.authType(),
                    command.remoteDirectory(),
                    command.scriptFilename(),
                    command.scriptContent());
            requireNameAvailable(projectId, deployment.getName(), null);
            deploymentRepository.save(deployment);
            if (deployment.getAuthType() == DeploymentAuthType.PASSWORD) {
                requirePassword(command.password());
                credentialStore.save(deployment.getId(), command.password());
            }
            return deployment;
        });
    }

    public Deployment update(DeploymentId id, SaveDeploymentCommand command) {
        return transactionRunner.inTransaction(() -> {
            Deployment deployment = deploymentRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("部署不存在：" + id.value()));
            if (!deployment.getProjectId().equals(requireProject(command.projectId()))) {
                throw new DomainException("部署归属项目创建后不能修改");
            }
            String nextName = command.name() == null ? deployment.getName() : command.name().trim();
            requireNameAvailable(deployment.getProjectId(), nextName, id);
            deployment.update(
                    nextName,
                    command.host(),
                    command.port(),
                    command.username(),
                    command.authType(),
                    command.remoteDirectory(),
                    command.scriptFilename(),
                    command.scriptContent());
            deploymentRepository.save(deployment);
            updateCredential(deployment, command.password());
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
            credentialStore.delete(id);
            deploymentRepository.delete(id);
            return null;
        });
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

    private void updateCredential(Deployment deployment, String password) {
        if (deployment.getAuthType() != DeploymentAuthType.PASSWORD) {
            credentialStore.delete(deployment.getId());
            return;
        }
        if (password != null && !password.isEmpty()) {
            credentialStore.save(deployment.getId(), password);
            return;
        }
        if (!credentialStore.exists(deployment.getId())) {
            throw new DomainException("密码认证需要填写服务器密码");
        }
    }

    private static void requirePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new DomainException("密码认证需要填写服务器密码");
        }
    }

    private static ProjectId requireProject(ProjectId projectId) {
        if (projectId == null) {
            throw new DomainException("请选择部署归属项目");
        }
        return projectId;
    }
}
