package com.pydlove.regent.deployment.application.command;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionMode;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionRequest;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionResult;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.deployment.domain.model.DeploymentRunStatus;
import com.pydlove.regent.deployment.domain.model.DeploymentReviewReference;
import com.pydlove.regent.deployment.domain.model.DeploymentSnapshot;
import com.pydlove.regent.deployment.domain.port.DeploymentExecutor;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.service.DeploymentScriptValidator;
import com.pydlove.regent.deployment.domain.service.ProjectReleaseScript;
import com.pydlove.regent.deployment.domain.port.DeploymentServerCredentialStore;
import com.pydlove.regent.deployment.domain.port.DeploymentLogStore;
import com.pydlove.regent.deployment.domain.port.DeploymentLogWriter;
import com.pydlove.regent.deployment.domain.repository.DeploymentRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentRunRepository;
import com.pydlove.regent.deployment.domain.repository.DeploymentServerRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.project.application.query.ProjectVersionService;
import com.pydlove.regent.project.application.query.ProjectVersionView;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.concurrent.ExecutorService;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 部署执行编排。
 *
 * <p>执行记录先入队再提交给独立线程池。密码在配置时进入独立凭据存储，
 * 执行发起时读取一次，只在这个调用链的内存里存在，不进入部署快照或日志。
 */
public class DeploymentExecutionService {

    public static final String RESTART_INTERRUPT_REASON = "平台重启，部署进程已退出";
    public static final String USER_INTERRUPT_REASON = "用户停止部署";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentRunRepository runRepository;
    private final DeploymentServerRepository serverRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final ProjectVersionService projectVersionService;
    private final DeploymentExecutor deploymentExecutor;
    private final DeploymentServerCredentialStore credentialStore;
    private final DeploymentLogStore logStore;
    private final ExecutorService workers;
    private final TransactionRunner transactionRunner;
    private final Set<Long> stopRequested = ConcurrentHashMap.newKeySet();

    public DeploymentExecutionService(DeploymentRepository deploymentRepository,
                                      DeploymentRunRepository runRepository,
                                      DeploymentServerRepository serverRepository,
                                      ProjectProfileRepository projectProfileRepository,
                                      DeploymentExecutor deploymentExecutor,
                                      DeploymentServerCredentialStore credentialStore,
                                      DeploymentLogStore logStore,
                                      ExecutorService workers,
                                      TransactionRunner transactionRunner) {
        this(deploymentRepository, runRepository, serverRepository, projectProfileRepository,
                projectProfileRepository == null
                        ? null
                        : new ProjectVersionService(projectProfileRepository, path -> java.util.Optional.empty()),
                deploymentExecutor, credentialStore, logStore, workers, transactionRunner);
    }

    public DeploymentExecutionService(DeploymentRepository deploymentRepository,
                                      DeploymentRunRepository runRepository,
                                      DeploymentServerRepository serverRepository,
                                      ProjectProfileRepository projectProfileRepository,
                                      ProjectVersionService projectVersionService,
                                      DeploymentExecutor deploymentExecutor,
                                      DeploymentServerCredentialStore credentialStore,
                                      DeploymentLogStore logStore,
                                      ExecutorService workers,
                                      TransactionRunner transactionRunner) {
        this.deploymentRepository = deploymentRepository;
        this.runRepository = runRepository;
        this.serverRepository = serverRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.projectVersionService = projectVersionService;
        this.deploymentExecutor = deploymentExecutor;
        this.credentialStore = credentialStore;
        this.logStore = logStore;
        this.workers = workers;
        this.transactionRunner = transactionRunner;
    }

    public DeploymentRun start(DeploymentId id) {
        return start(id, null, null);
    }

    public DeploymentRun start(DeploymentId id, ProjectId targetProjectId) {
        return start(id, targetProjectId, null);
    }

    public DeploymentRun start(DeploymentId id,
                               ProjectId targetProjectId,
                               DeploymentReviewReference reviewReference) {
        Started started = transactionRunner.inTransaction(() -> {
            Deployment deployment = deploymentRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("部署不存在：" + id.value()));
            if (runRepository.existsActiveByDeployment(id)) {
                throw new DomainException("该部署已有执行中的任务，请等它结束");
            }
            ProjectId executionProjectId = targetProjectId == null
                    ? deployment.getProjectId()
                    : targetProjectId;
            if (!deployment.belongsTo(executionProjectId)) {
                throw new DomainException("本次执行项目不属于该部署");
            }
            DeploymentServer server = serverRepository.findById(deployment.getServerId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "部署服务器不存在：" + deployment.getServerId().value()));
            ProjectProfile project = projectProfileRepository.findById(executionProjectId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "部署归属项目不存在：" + executionProjectId.value()));
            if (project.path() == null) {
                throw new DomainException("项目尚未绑定本机代码目录，不能执行部署");
            }
            DeploymentScriptValidator.requireValid(deployment.getScriptContent());
            String password = server.getAuthType() == DeploymentAuthType.PASSWORD
                    ? credentialStore.find(server.getId())
                            .filter(value -> !value.isEmpty())
                            .orElseThrow(() -> new DomainException(
                                    "尚未保存 SSH 密码，请先编辑服务器"))
                    : null;
            DeploymentSnapshot snapshot = deployment.snapshot(executionProjectId);
            if (snapshot.executionMode() == DeploymentExecutionMode.REMOTE_SCRIPT
                    && ProjectReleaseScript.isDualMode(snapshot.scriptContent())) {
                String resolvedPath = ProjectReleaseScript.resolvePath(
                        Path.of(project.path()), snapshot.scriptFilename(), snapshot.scriptContent());
                if (resolvedPath != null) {
                    snapshot = deployment.snapshot(
                            executionProjectId, DeploymentExecutionMode.PROJECT_SCRIPT, resolvedPath);
                }
            }
            ProjectVersionView projectVersion = projectVersionService == null
                    ? new ProjectVersionView(null, "NONE", false, "未配置项目版本解析")
                    : projectVersionService.resolve(executionProjectId);
            DeploymentRun queued = DeploymentRun.queue(
                    snapshot, server.snapshot(), reviewReference,
                    projectVersion.version(), projectVersion.source());
            runRepository.save(queued);
            return new Started(queued, password, project.path());
        });
        transactionRunner.afterCompletion(
                () -> submit(started.run().getId(), started.password(), started.projectDirectory()),
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

    /** 停止当前配置下排队中或执行中的部署。已经收场的记录不会被改写。 */
    public DeploymentRun stopActive(DeploymentId deploymentId) {
        DeploymentRun run = runRepository.findActiveByDeployment(deploymentId)
                .orElseThrow(() -> new DomainException("该部署当前没有执行中的任务"));
        return stop(run.getId());
    }

    DeploymentRun stop(DeploymentRunId runId) {
        DeploymentRun run = runRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundException("部署执行记录不存在：" + runId.value()));
        if (run.getStatus().isFinished()) {
            throw new DomainException("该部署已经结束，无需停止");
        }
        stopRequested.add(runId.value());
        deploymentExecutor.cancel(runId);
        try {
            return transactionRunner.inTransaction(() -> {
                DeploymentRun fresh = runRepository.findById(runId)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "部署执行记录不存在：" + runId.value()));
                if (!fresh.getStatus().isFinished()) {
                    fresh.interrupt(USER_INTERRUPT_REASON);
                    runRepository.save(fresh);
                }
                return fresh;
            });
        } catch (RuntimeException e) {
            stopRequested.remove(runId.value());
            throw e;
        }
    }

    private void submit(DeploymentRunId runId, String password, String projectDirectory) {
        try {
            workers.execute(() -> execute(runId, password, projectDirectory));
        } catch (RuntimeException e) {
            interrupt(runId, "部署工作线程未能启动：" + messageOf(e));
        }
    }

    private void execute(DeploymentRunId runId, String password, String projectDirectory) {
        DeploymentRun run = runRepository.findById(runId).orElse(null);
        if (run == null || !run.getStatus().isActive()) {
            stopRequested.remove(runId.value());
            return;
        }
        if (stopRequested.contains(runId.value()) || run.getStatus() == DeploymentRunStatus.INTERRUPTED) {
            interrupt(runId, USER_INTERRUPT_REASON);
            stopRequested.remove(runId.value());
            return;
        }

        run.begin();
        runRepository.save(run);
        if (stopRequested.contains(runId.value())) {
            run.interrupt(USER_INTERRUPT_REASON);
            runRepository.save(run);
            stopRequested.remove(runId.value());
            return;
        }
        DeploymentLogWriter writer = null;
        try {
            writer = logStore.open(runId);
            writer.append("[平台] 开始部署「" + run.getDeploymentName() + "」\n");
            writer.append("[平台] 目标 " + run.getUsername() + "@" + run.getHost()
                    + ":" + run.getPort() + "，远程目录 " + run.getRemoteDirectory() + "\n");
            writer.append("[平台] 脚本 " + run.getScriptFilename() + "\n");
            writer.append("[平台] 执行方式 "
                    + (run.getExecutionMode() == DeploymentExecutionMode.PROJECT_SCRIPT
                    ? "本机项目脚本 " + run.getProjectScriptPath()
                    : "远端脚本") + "\n\n");

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
                        run.getExecutionMode(),
                        run.getProjectScriptPath(),
                        password,
                        projectDirectory), writer);
            } catch (RuntimeException e) {
                result = new DeploymentExecutionResult(
                        DeploymentRunStatus.FAILED, null, "部署执行异常：" + messageOf(e));
            }
            if (stopRequested.contains(runId.value())) {
                result = new DeploymentExecutionResult(
                        DeploymentRunStatus.INTERRUPTED, null, USER_INTERRUPT_REASON);
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
            stopRequested.remove(runId.value());
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

    private record Started(DeploymentRun run, String password, String projectDirectory) {
    }
}
