package com.pydlove.regent.boot.config;

import com.pydlove.regent.deployment.application.command.DeploymentCommandService;
import com.pydlove.regent.deployment.application.command.DeploymentExecutionService;
import com.pydlove.regent.deployment.application.query.DeploymentQueryService;
import com.pydlove.regent.deployment.application.query.DeploymentRunQueryService;
import com.pydlove.regent.deployment.domain.port.DeploymentExecutor;
import com.pydlove.regent.deployment.domain.port.DeploymentCredentialStore;
import com.pydlove.regent.deployment.domain.port.DeploymentLogStore;
import com.pydlove.regent.deployment.domain.repository.DeploymentRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentRunRepository;
import com.pydlove.regent.deployment.infrastructure.log.FileDeploymentLogStore;
import com.pydlove.regent.deployment.infrastructure.credential.LocalDeploymentCredentialStore;
import com.pydlove.regent.deployment.infrastructure.process.ProcessDeploymentExecutor;
import com.pydlove.regent.deployment.infrastructure.persistence.DeploymentCredentialMapper;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** 部署上下文装配。部署线程池与 codex、测试用例线程池分开，避免长部署占住其它执行位。 */
@Configuration
public class DeploymentConfig {

    private static final Logger log = LoggerFactory.getLogger(DeploymentConfig.class);

    @Bean
    public DeploymentCommandService deploymentCommandService(
            DeploymentRepository deploymentRepository,
            DeploymentRunRepository deploymentRunRepository,
            ProjectProfileRepository projectProfileRepository,
            DeploymentCredentialStore credentialStore,
            TransactionRunner transactionRunner) {
        return new DeploymentCommandService(
                deploymentRepository, deploymentRunRepository, projectProfileRepository,
                credentialStore, transactionRunner);
    }

    @Bean
    public DeploymentQueryService deploymentQueryService(
            DeploymentRepository deploymentRepository,
            DeploymentRunRepository deploymentRunRepository,
            ProjectProfileRepository projectProfileRepository,
            DeploymentCredentialStore credentialStore) {
        return new DeploymentQueryService(
                deploymentRepository, deploymentRunRepository, projectProfileRepository,
                credentialStore);
    }

    @Bean
    public DeploymentCredentialStore deploymentCredentialStore(
            DeploymentCredentialMapper mapper,
            @Value("${regent.home}") String home) {
        return new LocalDeploymentCredentialStore(
                mapper, Path.of(home, "deployment", "credential.key"));
    }

    @Bean
    public DeploymentRunQueryService deploymentRunQueryService(
            DeploymentRunRepository deploymentRunRepository,
            DeploymentLogStore deploymentLogStore) {
        return new DeploymentRunQueryService(deploymentRunRepository, deploymentLogStore);
    }

    @Bean
    public DeploymentLogStore deploymentLogStore(
            @Value("${regent.home}") String home,
            @Value("${regent.deployment.max-log-chars:1048576}") int maxLogChars) {
        return new FileDeploymentLogStore(Path.of(home, "deployments", "runs"), maxLogChars);
    }

    @Bean
    public DeploymentExecutor deploymentExecutor(
            @Value("${regent.deployment.ssh-command:ssh}") String sshCommand,
            @Value("${regent.deployment.timeout:PT10M}") Duration timeout) {
        return new ProcessDeploymentExecutor(sshCommand, timeout);
    }

    @Bean(name = "deploymentWorkers", destroyMethod = "shutdown")
    public ExecutorService deploymentWorkers(
            @Value("${regent.deployment.workers:2}") int workers) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable,
                    "regent-deployment-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(Math.max(1, workers), factory);
    }

    @Bean
    public DeploymentExecutionService deploymentExecutionService(
            DeploymentRepository deploymentRepository,
            DeploymentRunRepository deploymentRunRepository,
            DeploymentExecutor deploymentExecutor,
            DeploymentCredentialStore credentialStore,
            DeploymentLogStore deploymentLogStore,
            @Qualifier("deploymentWorkers") ExecutorService deploymentWorkers,
            TransactionRunner transactionRunner) {
        return new DeploymentExecutionService(
                deploymentRepository,
                deploymentRunRepository,
                deploymentExecutor,
                credentialStore,
                deploymentLogStore,
                deploymentWorkers,
                transactionRunner);
    }

    /** 上一进程的 SSH 子进程已不存在，遗留的排队/执行中记录不能永久占着「执行中」。 */
    @Bean
    public ApplicationRunner deploymentRecoveryRunner(
            DeploymentExecutionService deploymentExecutionService) {
        return args -> {
            try {
                int interrupted = deploymentExecutionService.recoverInterruptedRuns();
                if (interrupted > 0) {
                    log.info("部署启动恢复完成：{} 条执行记录已标记为中断", interrupted);
                }
            } catch (RuntimeException e) {
                log.warn("部署启动恢复失败：{}", e.getMessage());
            }
        };
    }
}
