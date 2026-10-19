package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.DeploymentFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeploymentCommandServiceTest {

    private DeploymentFakes.Deployments deployments;
    private DeploymentFakes.Servers servers;
    private ProjectProfiles projects;
    private DeploymentCommandService service;
    private DeploymentServer server;

    @BeforeEach
    void setUp() {
        deployments = new DeploymentFakes.Deployments();
        servers = new DeploymentFakes.Servers();
        projects = new ProjectProfiles();
        projects.put(profile(7L, "app_audit"));
        server = servers.put(DeploymentServer.create(
                ProjectId.of(7L), "生产服务器", "api.example.com", 22,
                "root", DeploymentAuthType.KEY));
        service = new DeploymentCommandService(
                deployments, new DeploymentFakes.Runs(), projects, servers,
                new DirectTransactions());
    }

    @Test
    @DisplayName("部署创建后固定归属所选项目")
    void createsDeploymentWithOwnerProject() {
        Deployment deployment = service.create(command(7L, "生产 API"));

        assertEquals(ProjectId.of(7L), deployment.getProjectId());
        assertEquals("生产 API", deployment.getName());
    }

    @Test
    @DisplayName("不存在的项目不能挂部署")
    void rejectsUnknownProject() {
        assertThrows(EntityNotFoundException.class,
                () -> service.create(command(99L, "生产 API")));
    }

    @Test
    @DisplayName("编辑部署时不能变更归属项目")
    void rejectsProjectChangeOnUpdate() {
        Deployment deployment = service.create(command(7L, "生产 API"));

        assertThrows(DomainException.class,
                () -> service.update(deployment.getId(), command(8L, "生产 API")));
    }

    @Test
    @DisplayName("部署不能引用其他项目的服务器")
    void rejectsServerFromAnotherProject() {
        projects.put(profile(8L, "other"));
        DeploymentServer other = servers.put(DeploymentServer.create(
                ProjectId.of(8L), "其他服务器", "other.example.com", 22,
                "root", DeploymentAuthType.KEY));
        SaveDeploymentCommand command = new SaveDeploymentCommand(
                ProjectId.of(7L),
                other.getId(),
                "生产 API",
                "/opt/app",
                "deploy.sh",
                validScript("ok"));

        assertThrows(DomainException.class, () -> service.create(command));
    }

    @Test
    @DisplayName("不符合规范的脚本不能保存")
    void rejectsInvalidScript() {
        SaveDeploymentCommand command = new SaveDeploymentCommand(
                ProjectId.of(7L),
                server.getId(),
                "生产 API",
                "/opt/app",
                "deploy.sh",
                "echo ok");

        assertThrows(DomainException.class, () -> service.create(command));
    }

    private static SaveDeploymentCommand command(long projectId, String name) {
        return command(projectId, name, DeploymentServerId.of(1L));
    }

    private static SaveDeploymentCommand command(long projectId,
                                                 String name,
                                                 DeploymentServerId serverId) {
        return new SaveDeploymentCommand(
                ProjectId.of(projectId),
                serverId,
                name,
                "/opt/app",
                "deploy.sh",
                validScript("ok"));
    }

    private static String validScript(String message) {
        return """
                #!/usr/bin/env bash
                set -Eeuo pipefail
                echo %s
                """.formatted(message);
    }

    private static ProjectProfile profile(long id, String code) {
        return new ProjectProfile(
                ProjectId.of(id), code, code, "/tmp/" + code, DecisionLevel.L1, true);
    }

    private static final class ProjectProfiles implements ProjectProfileRepository {

        private final Map<Long, ProjectProfile> store = new LinkedHashMap<>();

        private void put(ProjectProfile profile) {
            store.put(profile.id().value(), profile);
        }

        @Override
        public Optional<ProjectProfile> findById(ProjectId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<ProjectProfile> findByCode(String code) {
            return store.values().stream()
                    .filter(profile -> profile.code().equals(code))
                    .findFirst();
        }

        @Override
        public List<ProjectProfile> findAll() {
            return List.copyOf(store.values());
        }
    }

    private static final class DirectTransactions implements TransactionRunner {

        @Override
        public <T> T inTransaction(Supplier<T> action) {
            return action.get();
        }

        @Override
        public void afterCompletion(Runnable onCommit, Runnable onRollback) {
            onCommit.run();
        }
    }
}
