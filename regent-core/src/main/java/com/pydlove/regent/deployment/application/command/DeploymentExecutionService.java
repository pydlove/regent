package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionRequest;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionResult;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.deployment.domain.model.DeploymentRunStatus;
import com.pydlove.regent.deployment.domain.port.DeploymentExecutor;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.service.DeploymentScriptValidator;
import com.pydlove.regent.deployment.domain.port.DeploymentServerCredentialStore;
import com.pydlove.regent.deployment.domain.port.DeploymentLogStore;
import com.pydlove.regent.deployment.domain.port.DeploymentLogWriter;
import com.pydlove.regent.deployment.domain.repository.DeploymentRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentRunRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentServerRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.concurrent.ExecutorService;

/**
 * 部署执行编排。
 *
 * <p>执行记录先入队再提交给独立线程池。密码在配置时进入独立凭据存储，
 * 执行发起时读取一次，只在这个调用链的内存里存在，不进入部署快照或日志。
 */
public class DeploymentExecutionService {

    public static final String RESTART_INTERRUPT_REASON = "平台重启，部署进程已退出";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentRunRepository runRepository;
    private final DeploymentServerRepository serverRepository;
    private final DeploymentExecutor deploymentExecutor;
    private final DeploymentServerCredentialStore credentialStore;
    private final DeploymentLogStore logStore;
    private final ExecutorService workers;
    private final TransactionRunner transactionRunner;

    public DeploymentExecutionService(DeploymentRepository deploymentRepository,
                                      DeploymentRunRepository runRepository,
                                      DeploymentServerRepository serverRepository,
                                      DeploymentExecutor deploymentExecutor,
                                      DeploymentServerCredentialStore credentialStore,
                                      DeploymentLogStore logStore,
                                      ExecutorService workers,
                                      TransactionRunner transactionRunner) {
        this.deploymentRepository = deploymentRepository;
        this.runRepository = runRepository;
        this.serverRepository = serverRepository;
        this.deploymentExecutor = deploymentExecutor;
        this.credentialStore = credentialStore;
        this.logStore = logStore;
        this.workers = workers;
        this.transactionRunner = transactionRunner;
    }

    public DeploymentRun start(DeploymentId id) {
        Started started = transactionRunner.inTransaction(() -> {
            Deployment deployment = deploymentRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("部署不存在：" + id.value()));
            if (runRepository.existsActiveByDeployment(id)) {
                throw new DomainException("该部署已有执行中的任务，请等它结束");
            }
            DeploymentServer server = serverRepository.findById(deployment.getServerId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "部署服务器不存在：" + deployment.getServerId().value()));
            DeploymentScriptValidator.requireValid(deployment.getScriptContent());
            String password = server.getAuthType() == DeploymentAuthType.PASSWORD
                    ? credentialStore.find(server.getId())
                            .filter(value -> !value.isEmpty())
                            .orElseThrow(() -> new DomainException(
                                    "尚未保存 SSH 密码，请先编辑服务器"))
                    : null;
            DeploymentRun queued = DeploymentRun.queue(deployment.snapshot(), server.snapshot());
            runRepository.save(queued);
            return new Started(queued, password);
        });
        transactionRunner.afterCompletion(
                () -> submit(started.run().getId(), started.password()),
                () -> interrupt(started.run().getId(), "发起部署的事务回滚，本次部署未执行"));
        return started.run();
    }

    /** 启动恢复：平台重启后，排队中/执行中的部署都失去了执行者。 */
    public int recoverInterruptedRuns() {
        int interrupted = 0;
        for (DeploymentRun run : runRepository.findActive()) {
            try {
                run.interrupt(RESTART_INTERRUPT_REASON);
                runRepository.save(run);
                interrupted++;
            } catch (RuntimeException ignored) {
                // 单条恢复失败不拖停其它记录，下一次启动还会看到它
            }
        }
        return interrupted;
    }

    private void submit(DeploymentRunId runId, String password) {
        try {
            workers.execute(() -> execute(runId, password));
        } catch (RuntimeException e) {
            interrupt(runId, "部署工作线程未能启动：" + messageOf(e));
        }
    }

    private void execute(DeploymentRunId runId, String password) {
        DeploymentRun run = runRepository.findById(runId).orElse(null);
        if (run == null || run.getStatus() != DeploymentRunStatus.QUEUED) {
            return;
        }

        run.begin();
        runRepository.save(run);
        DeploymentLogWriter writer = null;
        try {
            writer = logStore.open(runId);
            writer.append("[平台] 开始部署「" + run.getDeploymentName() + "」\n");
            writer.append("[平台] 目标 " + run.getUsername() + "@" + run.getHost()
                    + ":" + run.getPort() + "，远程目录 " + run.getRemoteDirectory() + "\n");
            writer.append("[平台] 脚本 " + run.getScriptFilename() + "\n\n");

            DeploymentExecutionResult result;
            try {
                result = deploymentExecutor.execute(new DeploymentExecutionRequest(
                        runId,
                        run.getProjectId(),
                        run.getHost(),
                        run.getPort(),
                        run.getUsername(),
                        run.getAuthType(),
                        run.getRemoteDirectory(),
                        run.getScriptFilename(),
                        run.getScriptContent(),
                        password), writer);
            } catch (RuntimeException e) {
                result = new DeploymentExecutionResult(
                        DeploymentRunStatus.FAILED, null, "部署执行异常：" + messageOf(e));
            }
            writer.append("\n[平台] " + (result.summary() == null ? "部署结束" : result.summary()) + "\n");
            run.finish(result);
            runRepository.save(run);
        } catch (RuntimeException e) {
            if (!run.getStatus().isFinished()) {
                try {
                    run.finish(new DeploymentExecutionResult(
                            DeploymentRunStatus.FAILED, null, "部署执行异常：" + messageOf(e)));
                    runRepository.save(run);
                } catch (RuntimeException ignored) {
                    // 保留原始异常路径，避免二次保存失败覆盖真正的失败原因
                }
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private void interrupt(DeploymentRunId runId, String reason) {
        try {
            DeploymentRun run = runRepository.findById(runId).orElse(null);
            if (run != null && !run.getStatus().isFinished()) {
                run.interrupt(reason);
                runRepository.save(run);
            }
        } catch (RuntimeException ignored) {
            // 留给下一次启动恢复或人工排查
        }
    }

    private static String messageOf(RuntimeException e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }

    private record Started(DeploymentRun run, String password) {
    }
}
