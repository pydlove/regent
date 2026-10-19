package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
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
    private DeploymentFakes.Credentials credentials;
    private ProjectProfiles projects;
    private DeploymentCommandService service;

    @BeforeEach
    void setUp() {
        deployments = new DeploymentFakes.Deployments();
        credentials = new DeploymentFakes.Credentials();
        projects = new ProjectProfiles();
        projects.put(profile(7L, "app_audit"));
        service = new DeploymentCommandService(
                deployments, new DeploymentFakes.Runs(), projects, credentials,
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
    @DisplayName("密码认证在配置时写入独立凭据存储，编辑留空时保留")
    void storesPasswordCredential() {
        Deployment deployment = service.create(command(
                7L, "生产 API", DeploymentAuthType.PASSWORD, "s3cret"));

        assertEquals("s3cret", credentials.password(deployment.getId()));

        service.update(deployment.getId(), command(
                7L, "生产 API", DeploymentAuthType.PASSWORD, ""));

        assertEquals("s3cret", credentials.password(deployment.getId()));
    }

    @Test
    @DisplayName("新建密码认证部署必须填写密码")
    void requiresPasswordOnCreate() {
        assertThrows(DomainException.class, () -> service.create(command(
                7L, "生产 API", DeploymentAuthType.PASSWORD, "")));
    }

    private static SaveDeploymentCommand command(long projectId, String name) {
        return command(projectId, name, DeploymentAuthType.KEY, null);
    }

    private static SaveDeploymentCommand command(long projectId,
                                                 String name,
                                                 DeploymentAuthType authType,
                                                 String password) {
        return new SaveDeploymentCommand(
                ProjectId.of(projectId),
                name,
                "api.example.com",
                22,
                "root",
                authType,
                "/opt/app",
                "deploy.sh",
                "echo ok",
                password);
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
