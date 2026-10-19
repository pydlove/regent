package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionResult;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentRunStatus;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.support.DeploymentFakes;
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentExecutionServiceTest {

    private DeploymentFakes.Deployments deployments;
    private DeploymentFakes.Runs runs;
    private DeploymentFakes.Servers servers;
    private DeploymentFakes.Logs logs;
    private DeploymentFakes.Credentials credentials;
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
        executor = new DeploymentFakes.ScriptedExecutor();
        service = new DeploymentExecutionService(
                deployments,
                runs,
                servers,
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
