package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionMode;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionResult;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.deployment.domain.model.DeploymentRunStatus;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.project.domain.model.ProjectVersionSource;
import com.pydlove.regent.project.application.query.ProjectVersionService;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.DeploymentFakes;
import com.pydlove.regent.support.ExecutionFakes;
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentExecutionServiceTest {

    @TempDir
    Path temp;

    private DeploymentFakes.Deployments deployments;
    private DeploymentFakes.Runs runs;
    private DeploymentFakes.Servers servers;
    private DeploymentFakes.Logs logs;
    private DeploymentFakes.Credentials credentials;
    private ExecutionFakes.Projects projects;
    private DeploymentFakes.ScriptedExecutor executor;
    private DeploymentExecutionService service;
    private Deployment deployment;
    private DeploymentServer server;

    @BeforeEach
    void setUp() {
        deployments = new DeploymentFakes.Deployments();
        runs = new DeploymentFakes.Runs();
        servers = new DeploymentFakes.Servers();
        logs = new DeploymentFakes.Logs();
        credentials = new DeploymentFakes.Credentials();
        projects = new ExecutionFakes.Projects()
                .put(new ProjectProfile(
                        ProjectId.of(7L),
                        "regent",
                        "Regent",
                        "/Users/panyong/shanjie/Regent",
                        DecisionLevel.L2,
                        true))
                .put(new ProjectProfile(
                        ProjectId.of(8L),
                        "other",
                        "Other",
                        "/tmp/other-project",
                        DecisionLevel.L2,
                        true));
        executor = new DeploymentFakes.ScriptedExecutor();
        service = new DeploymentExecutionService(
                deployments,
                runs,
                servers,
                projects,
                executor,
                credentials,
                logs,
                new DirectExecutorService(),
                new DirectTransactions());
        server = servers.put(DeploymentServer.create(
                ProjectId.of(7L), "生产服务器", "api.example.com", 22,
                "root", DeploymentAuthType.KEY));
        deployment = deployments.put(Deployment.create(
                ProjectId.of(7L), server.getId(), "生产",
                "/opt/app", "deploy.sh", validScript()));
    }

    @Test
    @DisplayName("密钥认证执行后写入成功记录和日志")
    void executesWithKeyAuth() {
        DeploymentRun run = service.start(deployment.getId());

        assertEquals(DeploymentRunStatus.SUCCESS, run.getStatus());
        assertEquals(1, runs.all().size());
        assertTrue(logs.read(run.getId()).contains("开始部署"));
        assertTrue(logs.read(run.getId()).contains("fake output"));
        assertEquals(null, executor.requests().get(0).password());
        assertEquals(ProjectId.of(7L), executor.requests().get(0).projectId());
        assertEquals("/Users/panyong/shanjie/Regent",
                executor.requests().get(0).projectDirectory());
    }

    @Test
    @DisplayName("部署入队时快照项目版本")
    void snapshotsProjectVersion() {
        projects.put(new ProjectProfile(
                ProjectId.of(7L),
                null,
                "regent",
                "Regent",
                "/Users/panyong/shanjie/Regent",
                DecisionLevel.L2,
                true,
                ProjectSummaryStatus.NOT_GENERATED,
                null,
                null,
                null,
                ProjectVersionSource.MANUAL,
                "v2.0.0"));
        DeploymentExecutionService versioned = new DeploymentExecutionService(
                deployments,
                runs,
                servers,
                projects,
                new ProjectVersionService(projects, path -> Optional.empty()),
                executor,
                credentials,
                logs,
                new DirectExecutorService(),
                new DirectTransactions());

        DeploymentRun run = versioned.start(deployment.getId());

        assertEquals("v2.0.0", run.getProjectVersion());
        assertEquals("MANUAL", run.getVersionSource());
    }

    @Test
    @DisplayName("项目发布脚本携带项目目录、脚本路径和执行方式")
    void passesProjectScriptContext() {
        Deployment projectDeployment = deployments.put(Deployment.create(
                ProjectId.of(7L), server.getId(), "本机完整发布",
                "/root/app", "deploy.sh", validScript(),
                DeploymentExecutionMode.PROJECT_SCRIPT, "deploy/remote/deploy.sh"));

        service.start(projectDeployment.getId());

        var request = executor.requests().get(0);
        assertEquals(DeploymentExecutionMode.PROJECT_SCRIPT, request.executionMode());
        assertEquals("deploy/remote/deploy.sh", request.projectScriptPath());
        assertEquals("/Users/panyong/shanjie/Regent", request.projectDirectory());
    }

    @Test
    @DisplayName("历史远端配置遇到双模式脚本时自动按项目发布脚本执行")
    void upgradesDualModeScriptToProjectExecution() throws Exception {
        String script = """
                #!/usr/bin/env bash
                set -Eeuo pipefail
                # 本地发布：deploy.sh -H/--host HOST
                HOST=""
                run_local() { echo local; }
                run_remote() { echo remote; }
                if [ -n "$HOST" ]; then
                    run_local
                else
                    run_remote
                fi
                """;
        Path projectRoot = temp.resolve("dual-mode-project");
        Path scriptFile = projectRoot.resolve("deploy/deploy_voice_test_platform.sh");
        Files.createDirectories(scriptFile.getParent());
        Files.writeString(scriptFile, script, StandardCharsets.UTF_8);
        projects.put(new ProjectProfile(
                ProjectId.of(9L),
                "voice",
                "Voice",
                projectRoot.toString(),
                DecisionLevel.L2,
                true));
        DeploymentServer dualServer = servers.put(DeploymentServer.create(
                ProjectId.of(9L), "声音靶机", "api.example.com", 22,
                "root", DeploymentAuthType.KEY));
        Deployment dualModeDeployment = deployments.put(Deployment.create(
                ProjectId.of(9L), dualServer.getId(), "声音平台",
                "/root/app/voice", "deploy_voice_test_platform.sh", script));

        DeploymentRun run = service.start(dualModeDeployment.getId());

        var request = executor.requests().get(0);
        assertEquals(DeploymentExecutionMode.PROJECT_SCRIPT, request.executionMode());
        assertEquals("deploy/deploy_voice_test_platform.sh", request.projectScriptPath());
        assertEquals(projectRoot.toString(), request.projectDirectory());
        assertTrue(logs.read(run.getId()).contains(
                "本机项目脚本 deploy/deploy_voice_test_platform.sh"));
    }

    @Test
    @DisplayName("多项目部署按本次选择的项目初始化工作区")
    void executesInSelectedAssociatedProject() {
        Collection<ProjectId> projectIds = List.of(ProjectId.of(7L), ProjectId.of(8L));
        Deployment shared = deployments.put(Deployment.create(
                projectIds,
                servers.put(DeploymentServer.create(
                        List.of(ProjectId.of(7L), ProjectId.of(8L)),
                        "共享服务器", "api.example.com", 22,
                        "root", DeploymentAuthType.KEY)).getId(),
                "多项目部署", "/opt/app", "deploy.sh", validScript()));

        DeploymentRun run = service.start(shared.getId(), ProjectId.of(8L));

        assertEquals(ProjectId.of(8L), run.getProjectId());
        assertEquals(ProjectId.of(8L), executor.requests().get(0).projectId());
        assertEquals("/tmp/other-project", executor.requests().get(0).projectDirectory());
    }

    @Test
    @DisplayName("不能使用部署未关联的项目执行")
    void rejectsUnrelatedRunProject() {
        assertThrows(RuntimeException.class,
                () -> service.start(deployment.getId(), ProjectId.of(8L)));
        assertTrue(runs.all().isEmpty());
    }

    @Test
    @DisplayName("密码认证读取配置时保存的密码，密码只传给执行器")
    void usesStoredPasswordForPasswordAuth() {
        Deployment passwordDeployment = deployments.put(Deployment.create(
                ProjectId.of(7L), servers.put(DeploymentServer.create(
                        ProjectId.of(7L), "密码服务器", "api.example.com", 22,
                        "deploy", DeploymentAuthType.PASSWORD)).getId(),
                "密码部署",
                "~", "deploy.sh", validScript()));

        assertThrows(RuntimeException.class, () -> service.start(passwordDeployment.getId()));

        credentials.save(passwordDeployment.getServerId(), "s3cret");
        service.start(passwordDeployment.getId());
        assertEquals("s3cret", executor.requests().get(0).password());
    }

    @Test
    @DisplayName("启动恢复把排队中的部署标记为中断")
    void recoversInterruptedRuns() {
        DeploymentRun queued = DeploymentRun.queue(deployment.snapshot(), server.snapshot());
        runs.save(queued);

        int interrupted = service.recoverInterruptedRuns();

        assertEquals(1, interrupted);
        assertEquals(DeploymentRunStatus.INTERRUPTED, runs.all().get(0).getStatus());
    }

    @Test
    @DisplayName("停止部署会通知执行器并把活动记录收敛为中断")
    void stopsActiveRun() {
        DeploymentRun run = DeploymentRun.queue(deployment.snapshot(), server.snapshot());
        run.assignIdentity(DeploymentRunId.of(15L));
        run.begin();
        runs.save(run);

        DeploymentRun stopped = service.stopActive(deployment.getId());

        assertEquals(DeploymentRunStatus.INTERRUPTED, stopped.getStatus());
        assertEquals(DeploymentExecutionService.USER_INTERRUPT_REASON, stopped.getSummary());
        assertEquals(List.of(15L), executor.cancelledRunIds());
    }

    @Test
    @DisplayName("历史遗留的不规范脚本不能开始执行")
    void rejectsInvalidStoredScript() {
        Deployment invalid = deployments.put(Deployment.create(
                ProjectId.of(7L), server.getId(), "遗留部署",
                "/opt/app", "legacy.sh", "echo legacy"));

        assertThrows(RuntimeException.class, () -> service.start(invalid.getId()));
        assertTrue(runs.all().isEmpty());
    }

    private static final class DirectExecutorService extends AbstractExecutorService {

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    private static String validScript() {
        return """
                #!/usr/bin/env bash
                set -Eeuo pipefail
                echo ok
                """;
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
