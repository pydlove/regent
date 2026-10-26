package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 服务器资源可复用，凭据只按服务器 ID 保存。 */
class DeploymentServerCommandServiceTest {

    private DeploymentFakes.Servers servers;
    private DeploymentFakes.Deployments deployments;
    private DeploymentFakes.Runs runs;
    private DeploymentFakes.Credentials credentials;
    private DeploymentServerCommandService service;

    @BeforeEach
    void setUp() {
        servers = new DeploymentFakes.Servers();
        deployments = new DeploymentFakes.Deployments();
        runs = new DeploymentFakes.Runs();
        credentials = new DeploymentFakes.Credentials();
        ProjectProfiles projects = new ProjectProfiles();
        projects.put(new ProjectProfile(
                ProjectId.of(7L), "regent", "Regent", "/tmp/regent",
                DecisionLevel.L1, true));
        projects.put(new ProjectProfile(
                ProjectId.of(8L), "console", "Console", "/tmp/console",
                DecisionLevel.L1, true));
        service = new DeploymentServerCommandService(
                servers, deployments, runs, projects, credentials, new DirectTransactions());
    }

    @Test
    @DisplayName("密码认证服务器把密码保存到独立凭据存储")
    void storesPasswordCredential() {
        DeploymentServer server = service.create(new SaveDeploymentServerCommand(
                ProjectId.of(7L), "生产服务器", "api.example.com", 22, "root",
                DeploymentAuthType.PASSWORD, "s3cret"));

        assertEquals("s3cret", credentials.password(server.getId()));
    }

    @Test
    @DisplayName("新建和编辑服务器时可维护多个归属项目")
    void maintainsMultipleProjects() {
        DeploymentServer server = service.create(new SaveDeploymentServerCommand(
                List.of(ProjectId.of(7L), ProjectId.of(8L)),
                "共享服务器", "api.example.com", 22, "root",
                DeploymentAuthType.KEY, null, null));

        assertEquals(
                List.of(ProjectId.of(7L), ProjectId.of(8L)),
                server.getProjectIds());

        DeploymentServer updated = service.update(
                server.getId(),
                new SaveDeploymentServerCommand(
                        List.of(ProjectId.of(8L)),
                        "共享服务器", "api.example.com", 22, "root",
                        DeploymentAuthType.KEY, null, null));

        assertEquals(List.of(ProjectId.of(8L)), updated.getProjectIds());
    }

    @Test
    @DisplayName("服务器归属项目必须全部存在")
    void rejectsUnknownProject() {
        assertThrows(EntityNotFoundException.class, () -> service.create(
                new SaveDeploymentServerCommand(
                        List.of(ProjectId.of(7L), ProjectId.of(99L)),
                        "共享服务器", "api.example.com", 22, "root",
                        DeploymentAuthType.KEY, null, null)));
    }

    @Test
    @DisplayName("删除服务器时级联软删除关联部署配置")
    void deletesServerAndAssociatedDeployments() {
        DeploymentServer server = service.create(new SaveDeploymentServerCommand(
                ProjectId.of(7L), "生产服务器", "api.example.com", 22, "root",
                DeploymentAuthType.PASSWORD, "s3cret"));
        Deployment first = Deployment.create(
                ProjectId.of(7L), server.getId(), "部署一",
                "/opt/app", "one.sh", "echo one");
        deployments.save(first);
        Deployment second = Deployment.create(
                ProjectId.of(7L), server.getId(), "部署二",
                "/opt/app", "two.sh", "echo two");
        deployments.save(second);

        service.delete(server.getId());

        assertEquals(Optional.empty(), servers.findById(server.getId()));
        assertEquals(Optional.empty(), deployments.findById(first.getId()));
        assertEquals(Optional.empty(), deployments.findById(second.getId()));
        assertNull(credentials.password(server.getId()));
    }

    @Test
    @DisplayName("关联部署执行中时仍阻止删除服务器")
    void keepsServerWhileAssociatedDeploymentIsActive() {
        DeploymentServer server = service.create(new SaveDeploymentServerCommand(
                ProjectId.of(7L), "生产服务器", "api.example.com", 22, "root",
                DeploymentAuthType.KEY, null));
        Deployment deployment = Deployment.create(
                ProjectId.of(7L), server.getId(), "部署一",
                "/opt/app", "one.sh", "echo one");
        deployments.save(deployment);
        runs.save(DeploymentRun.queue(deployment.snapshot(), server.snapshot()));

        assertThrows(DomainException.class, () -> service.delete(server.getId()));
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
            return store.values().stream().filter(profile -> profile.code().equals(code)).findFirst();
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
