package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.application.query.GeneratedDeploymentScript;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentScriptGenerationRequest;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.domain.repository.DeploymentServerRepository;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentScriptGenerationServiceTest {

    @TempDir
    Path tempDir;

    private Projects projects;
    private Servers servers;
    private Project project;
    private DeploymentServer server;
    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        Path projectRoot = Files.createDirectories(tempDir.resolve("project"));
        workspace = Files.createDirectories(projectRoot.resolve("service"));
        project = Project.register(
                "regent", "Regent", projectRoot.toString(),
                "Java 17 / Maven", "mvn test", DecisionLevel.L1);
        project.assignIdentity(ProjectId.of(7L));
        projects = new Projects(project);

        server = DeploymentServer.create(
                ProjectId.of(7L), "生产服务器", "api.example.com", 22,
                "root", DeploymentAuthType.KEY);
        server.assignIdentity(DeploymentServerId.of(9L));
        servers = new Servers(server);
    }

    @Test
    @DisplayName("生成脚本时使用所选项目文件夹，并返回规范文件名和正文")
    void generatesValidatedScript() throws Exception {
        AtomicReference<DeploymentScriptGenerationRequest> captured = new AtomicReference<>();
        var service = new DeploymentScriptGenerationService(projects, servers, request -> {
            captured.set(request);
            return """
                    #!/usr/bin/env bash
                    set -Eeuo pipefail
                    printf 'deploy %s\\n' "$REGENT_DEPLOY_DIRECTORY"
                    """;
        });

        GeneratedDeploymentScript generated = service.generate(command(workspace.toString()));

        assertEquals("deploy_regent.sh", generated.scriptFilename());
        assertTrue(generated.scriptContent().startsWith("#!/usr/bin/env bash"));
        assertTrue(generated.scriptContent().endsWith("\n"));
        assertEquals(workspace.toRealPath().toString(), captured.get().workspacePath());
        assertEquals("/opt/regent", captured.get().remoteDirectory());
        assertEquals("api.example.com", captured.get().serverHost());
    }

    @Test
    @DisplayName("项目文件夹不能逃出项目根目录")
    void rejectsWorkspaceOutsideProject() throws Exception {
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        var service = new DeploymentScriptGenerationService(projects, servers, request -> validScript());

        DomainException error = assertThrows(DomainException.class,
                () -> service.generate(command(outside.toString())));

        assertTrue(error.getMessage().contains("必须位于项目目录内"));
    }

    @Test
    @DisplayName("Codex 生成的脚本不满足平台规范时直接拒绝")
    void rejectsInvalidGeneratedScript() {
        var service = new DeploymentScriptGenerationService(
                projects, servers, request -> "echo deploy\n");

        DomainException error = assertThrows(DomainException.class,
                () -> service.generate(command(workspace.toString())));

        assertTrue(error.getMessage().contains("部署脚本不符合规范"));
    }

    @Test
    @DisplayName("停止请求会中断正在执行的生成线程")
    void cancelsActiveGeneration() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        var service = new DeploymentScriptGenerationService(projects, servers, request -> {
            started.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return validScript();
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(() -> service.generate(
                    command(workspace.toString()), "cancel-test-123"));
            assertTrue(started.await(2, TimeUnit.SECONDS));

            assertTrue(service.cancel("cancel-test-123"));
            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> future.get(2, TimeUnit.SECONDS));
            assertTrue(error.getCause() instanceof DomainException);
            assertTrue(error.getCause().getMessage().contains("已停止"));
            assertFalse(service.cancel("missing-test-123"));
        } finally {
            executor.shutdownNow();
        }
    }

    private GenerateDeploymentScriptCommand command(String workspacePath) {
        return new GenerateDeploymentScriptCommand(
                ProjectId.of(7L), DeploymentServerId.of(9L), workspacePath, "/opt/regent");
    }

    private static String validScript() {
        return """
                #!/usr/bin/env bash
                set -Eeuo pipefail
                echo deploy
                """;
    }

    private record Projects(Project project) implements ProjectRepository {

        @Override
        public boolean existsByCode(String code) {
            return project.getCode().equals(code);
        }

        @Override
        public void save(Project ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Project> findById(ProjectId id) {
            return project.getId().equals(id) ? Optional.of(project) : Optional.empty();
        }

        @Override
        public List<Project> findBySummaryStatus(
                com.pydlove.regent.project.domain.model.ProjectSummaryStatus status) {
            return List.of();
        }

        @Override
        public void update(Project ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateDetails(Project ignored) {
            throw new UnsupportedOperationException();
        }
    }

    private record Servers(DeploymentServer server) implements DeploymentServerRepository {

        @Override
        public Optional<DeploymentServer> findById(DeploymentServerId id) {
            return server.getId().equals(id) ? Optional.of(server) : Optional.empty();
        }

        @Override
        public Optional<DeploymentServer> findByIdForUpdate(DeploymentServerId id) {
            return findById(id);
        }

        @Override
        public List<DeploymentServer> findAll(ProjectId projectId) {
            return server.belongsTo(projectId) ? List.of(server) : List.of();
        }

        @Override
        public boolean existsByName(java.util.Collection<ProjectId> projectIds, String name,
                                    DeploymentServerId excludingId) {
            return false;
        }

        @Override
        public void save(DeploymentServer ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(DeploymentServerId id) {
            throw new UnsupportedOperationException();
        }
    }
}
