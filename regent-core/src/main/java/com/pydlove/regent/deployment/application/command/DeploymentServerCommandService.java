package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.domain.port.DeploymentServerCredentialStore;
import com.pydlove.regent.deployment.domain.repository.DeploymentRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentRunRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentServerRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.List;

/** 可复用部署服务器的写侧用例。 */
public class DeploymentServerCommandService {

    private final DeploymentServerRepository serverRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentRunRepository runRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final DeploymentServerCredentialStore credentialStore;
    private final TransactionRunner transactionRunner;

    public DeploymentServerCommandService(DeploymentServerRepository serverRepository,
                                          DeploymentRepository deploymentRepository,
                                          DeploymentRunRepository runRepository,
                                          ProjectProfileRepository projectProfileRepository,
                                          DeploymentServerCredentialStore credentialStore,
                                          TransactionRunner transactionRunner) {
        this.serverRepository = serverRepository;
        this.deploymentRepository = deploymentRepository;
        this.runRepository = runRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.credentialStore = credentialStore;
        this.transactionRunner = transactionRunner;
    }

    public DeploymentServer create(SaveDeploymentServerCommand command) {
        return transactionRunner.inTransaction(() -> {
            ProjectId projectId = requireProject(command.projectId());
            requireProjectExists(projectId);
            DeploymentServer server = DeploymentServer.create(
                    projectId,
                    command.name(),
                    command.host(),
                    command.port(),
                    command.username(),
                    command.authType(),
                    command.defaultRemoteDirectory());
            requireNameAvailable(projectId, server.getName(), null);
            serverRepository.save(server);
            if (server.getAuthType() == DeploymentAuthType.PASSWORD) {
                requirePassword(command.password());
                credentialStore.save(server.getId(), command.password());
            }
            return server;
        });
    }

    public DeploymentServer update(DeploymentServerId id, SaveDeploymentServerCommand command) {
        return transactionRunner.inTransaction(() -> {
            DeploymentServer server = serverRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("部署服务器不存在：" + id.value()));
            if (!server.getProjectId().equals(requireProject(command.projectId()))) {
                throw new DomainException("服务器归属项目创建后不能修改");
            }
            String nextName = command.name() == null ? server.getName() : command.name().trim();
            requireNameAvailable(server.getProjectId(), nextName, id);
            server.update(nextName, command.host(), command.port(), command.username(),
                    command.authType(), command.defaultRemoteDirectory());
            serverRepository.save(server);
            updateCredential(server, command.password());
            return server;
        });
    }

    public void delete(DeploymentServerId id) {
        transactionRunner.inTransaction(() -> {
            serverRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("部署服务器不存在：" + id.value()));
            List<DeploymentId> deploymentIds = deploymentRepository.findIdsByServerId(id);
            if (deploymentIds.stream().anyMatch(runRepository::existsActiveByDeployment)) {
                throw new DomainException("关联部署正在执行，不能删除服务器；请等执行结束后再试");
            }
            deploymentIds.forEach(deploymentRepository::delete);
            credentialStore.delete(id);
            serverRepository.delete(id);
            return null;
        });
    }

    private void requireProjectExists(ProjectId projectId) {
        if (projectProfileRepository.findById(projectId).isEmpty()) {
            throw new EntityNotFoundException("项目不存在：" + projectId);
        }
    }

    private void requireNameAvailable(ProjectId projectId,
                                      String name,
                                      DeploymentServerId excludingId) {
        if (serverRepository.existsByName(projectId, name, excludingId)) {
            throw new DomainException("该项目已存在同名服务器：" + name);
        }
    }

    private void updateCredential(DeploymentServer server, String password) {
        if (server.getAuthType() != DeploymentAuthType.PASSWORD) {
            credentialStore.delete(server.getId());
            return;
        }
        if (password != null && !password.isEmpty()) {
            credentialStore.save(server.getId(), password);
            return;
        }
        if (!credentialStore.exists(server.getId())) {
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
            throw new DomainException("请选择服务器归属项目");
        }
        return projectId;
    }
}
