package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.ExecReport;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.ExecutionDevice;
import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskAdjustmentId;
import com.pydlove.regent.execution.domain.model.TaskAttachment;
import com.pydlove.regent.execution.domain.model.TaskDocument;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.port.BranchManager;
import com.pydlove.regent.execution.domain.port.ExecutorContract;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.execution.domain.repository.ExecutionDeviceRepository;
import com.pydlove.regent.execution.domain.repository.TaskAttachmentRepository;
import com.pydlove.regent.execution.domain.repository.TaskDocumentRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.execution.domain.service.ExecutionConcurrencyGate;
import com.pydlove.regent.execution.application.query.AgentTaskSummary;
import com.pydlove.regent.governance.application.command.RaisedDecision;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionStatus;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
import com.pydlove.regent.plugin.application.query.PluginContext;
import com.pydlove.regent.plugin.domain.repository.TaskPluginRepository;
import com.pydlove.regent.project.application.query.EnvironmentContext;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部执行者 —— 认领、心跳、上报、失联回收。
 *
 * <p>平台原先只有一种执行者：它自己在本机拉起的 {@code codex exec}（见 {@link TaskRunService}）。
 * 这个类让「已经在用的那些模型工具」也能干活：codex 桌面版、别的 CLI、别的机器上的模型，
 * 通过 HTTP 认领一条任务，在自己的环境里做完，把同一份结构化出参报回来。
 *
 * <h2>为什么不是「把执行器换个实现」那么简单</h2>
 * 内部执行器是<b>同步</b>的：{@code execute()} 返回就代表这一轮结束了，所以一次尝试可以
 * 老老实实占着一个方法栈。外部执行者不是 —— 它可能在几分钟后、甚至第二天才回报，
 * 平台不能挂一个线程等它。于是「一次尝试」被拆成三段，状态全落在库里：
 *
 * <pre>
 *   claim ──▶ 落 task_run（RUNNING + 租约）──▶ 执行者在自己的环境里干活
 *                                                │
 *                          report ──▶ 与内部路径同一套结算（{@link RunSettlementService}）
 *                                                │
 *         心跳失约 ──▶ releaseExpiredLeases ──▶ 判失联、留痕、放掉任务
 * </pre>
 *
 * <h2>租约是这个类唯一的活性证明</h2>
 * 外部执行者跑在平台看不见也杀不掉的进程里。没有租约，用户把编辑器一关，任务就永远停在
 * 「执行中」，而工作台上没有任何线索。所以：认领即拿租约、心跳续租、到期由回收任务判失联。
 *
 * <h2>并发只由平台闸门决定</h2>
 * 任务能否认领只看平台全局并发上限，不额外按项目加锁。同一项目下的多个任务可以在不同设备
 * 或不同执行者上并行推进。
 */
public class AgentRunService implements DeviceRestartRecoveryHandler {

    /** 协议版本。执行者看到不认识的版本就该拒绝开工，而不是猜字段。 */
    public static final String PROTOCOL = "regent-agent/1";

    /** 没指定任务时，一次扫描多少条候选。 */
    private static final int CANDIDATE_SCAN = 20;
    private static final Duration DEVICE_ONLINE_TTL = Duration.ofSeconds(45);

    /** 一次尝试内允许的连续决策轮次上限，与内部执行者同一把尺子。 */
    public static final int MAX_DECISION_ROUNDS = TaskRunService.MAX_DECISION_ROUNDS;

    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final ExecutionDeviceRepository executionDeviceRepository;
    private final RunEventRepository runEventRepository;
    private final TaskAttachmentRepository attachmentRepository;
    private final TaskDocumentRepository documentRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final EnvironmentEntryRepository environmentEntryRepository;
    private final TaskPluginRepository taskPluginRepository;
    private final TaskCodeStandardService taskCodeStandardService;
    private final DecisionRepository decisionRepository;
    private final BranchManager branchManager;
    private final BranchProvisioningService branchProvisioningService;
    private final RunSettlementService settlement;
    private final ExecutorContract contract;
    private final ExecutionConcurrencyGate concurrencyGate;
    private final TransactionRunner transactionRunner;
    private final DeviceRestartRecoveryService deviceRestartRecoveryService;
    private final Duration lease;
    private final String contractUrl;
    private final Map<Long, ExecutionConcurrencyGate.Permit> activePermits = new ConcurrentHashMap<>();
    /** 本次认领实际带出的调整。只有它被再次上报时才标记下发，避免把执行期间新加的方向误记为已处理。 */
    private final Map<Long, List<TaskAdjustmentId>> deliveredAdjustments = new ConcurrentHashMap<>();

    public AgentRunService(TaskRepository taskRepository,
                           TaskRunRepository taskRunRepository,
                           ExecutionDeviceRepository executionDeviceRepository,
                           RunEventRepository runEventRepository,
                           TaskAttachmentRepository attachmentRepository,
                           TaskDocumentRepository documentRepository,
                           ProjectProfileRepository projectProfileRepository,
                           EnvironmentEntryRepository environmentEntryRepository,
                           TaskPluginRepository taskPluginRepository,
                           DecisionRepository decisionRepository,
                           BranchManager branchManager,
                           BranchProvisioningService branchProvisioningService,
                           RunSettlementService settlement,
                           ExecutorContract contract,
                           ExecutionConcurrencyGate concurrencyGate,
                           TransactionRunner transactionRunner,
                           DeviceRestartRecoveryService deviceRestartRecoveryService,
                           Duration lease,
                           String contractUrl) {
        this(taskRepository, taskRunRepository, executionDeviceRepository, runEventRepository,
                attachmentRepository, documentRepository, projectProfileRepository,
                environmentEntryRepository, taskPluginRepository, decisionRepository,
                branchManager, branchProvisioningService, settlement, contract, concurrencyGate,
                transactionRunner, deviceRestartRecoveryService, lease, contractUrl, null);
    }

    public AgentRunService(TaskRepository taskRepository,
                           TaskRunRepository taskRunRepository,
                           ExecutionDeviceRepository executionDeviceRepository,
                           RunEventRepository runEventRepository,
                           TaskAttachmentRepository attachmentRepository,
                           TaskDocumentRepository documentRepository,
                           ProjectProfileRepository projectProfileRepository,
                           EnvironmentEntryRepository environmentEntryRepository,
                           TaskPluginRepository taskPluginRepository,
                           DecisionRepository decisionRepository,
                           BranchManager branchManager,
                           BranchProvisioningService branchProvisioningService,
                           RunSettlementService settlement,
                           ExecutorContract contract,
                           ExecutionConcurrencyGate concurrencyGate,
                           TransactionRunner transactionRunner,
                           DeviceRestartRecoveryService deviceRestartRecoveryService,
                           Duration lease,
                           String contractUrl,
                           TaskCodeStandardService taskCodeStandardService) {
        this.taskRepository = taskRepository;
        this.taskRunRepository = taskRunRepository;
        this.executionDeviceRepository = executionDeviceRepository;
        this.runEventRepository = runEventRepository;
        this.attachmentRepository = attachmentRepository;
        this.documentRepository = documentRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.environmentEntryRepository = environmentEntryRepository;
        this.taskPluginRepository = taskPluginRepository;
        this.taskCodeStandardService = taskCodeStandardService;
        this.decisionRepository = decisionRepository;
        this.branchManager = branchManager;
        this.branchProvisioningService = branchProvisioningService;
        this.settlement = settlement;
        this.contract = contract;
        this.concurrencyGate = concurrencyGate;
        this.transactionRunner = transactionRunner;
        this.deviceRestartRecoveryService = deviceRestartRecoveryService;
        this.lease = lease;
        this.contractUrl = contractUrl;
        restoreActiveLeases(Instant.now());
    }

    @Override
    public int recover(String deviceId, String reason) {
        List<TaskRunId> recovered = deviceRestartRecoveryService.recover(deviceId, reason);
        recovered.forEach(this::releasePermit);
        return recovered.size();
    }

    /**
     * 认领一条任务。没有可领的活时返回空 —— 空不是错误，是「现在没活」这句实话。
     *
     * @param agentName   执行者自称：谁在干活，界面上要显示它
     * @param sessionId   执行者的会话标识，平台拿它当续跑钥匙（断点续跑靠它把活交回同一个会话）
     * @param projectCode 只看这个项目下的活（人把「地址 + 项目」交给执行者时就是它）；为空表示不限
     * @param taskId      指定任务（批准之后回来续跑时用）；为空则自己挑一条
     */
    public Optional<AgentWorkPackage> claim(String agentName, String sessionId, String projectCode, Long taskId) {
        return claim(agentName, sessionId, projectCode, taskId, null);
    }

    /**
     * 认领一条任务。设备协议会额外带上 deviceId，用于服务端自检和租约归属校验。
     */
    public Optional<AgentWorkPackage> claim(String agentName,
                                            String sessionId,
                                            String projectCode,
                                            Long taskId,
                                            String deviceId) {
        requireText(agentName, "执行者");
        requireText(sessionId, "会话标识");
        Instant now = now();

        Task requestedTask = taskId == null ? null : loadTask(TaskId.of(taskId));
        if (requestedTask != null && !claimable(requestedTask)) {
            throw new DomainException("任务当前状态不可认领：" + requestedTask.getId()
                    + "，状态：" + requestedTask.getStatus());
        }
        ProjectProfile requestedProject = requestedTask != null || projectCode == null || projectCode.isBlank()
                ? null
                : loadProjectByCode(projectCode);

        Optional<ExecutionConcurrencyGate.Permit> acquired = concurrencyGate.tryAcquire();
        if (acquired.isEmpty()) {
            if (taskId != null || (projectCode != null && !projectCode.isBlank())) {
                throw new DomainException("平台同时运行的任务已达到上限 " + concurrencyGate.limit()
                        + "，等有任务收场后再认领");
            }
            return Optional.empty();
        }
        ExecutionConcurrencyGate.Permit permit = acquired.orElseThrow();

        try {
            Optional<AgentWorkPackage> work = transactionRunner.inTransaction(() -> {
                Task task;
                if (requestedTask != null) {
                    task = taskRepository.findByIdForUpdate(requestedTask.getId())
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "任务不存在：" + requestedTask.getId()));
                    if (!claimable(task)) {
                        throw new DomainException("任务当前状态不可认领：" + task.getId()
                                + "，状态：" + task.getStatus());
                    }
                } else if (requestedProject != null) {
                    task = lockCandidateIn(requestedProject);
                } else {
                    task = lockCandidate();
                }
                if (task == null) {
                    return Optional.empty();
                }

                ProjectProfile project = preflight(task, deviceId, now);
                Optional<AgentWorkPackage> claimed =
                        startAttempt(task, project, agentName, sessionId, deviceId, now);
                claimed.ifPresent(ignored -> markDeviceClaimed(deviceId, now));
                return claimed;
            });
            if (work.isEmpty()) {
                permit.release();
                return Optional.empty();
            }
            activePermits.put(work.orElseThrow().runId(), permit);
            return work;
        } catch (RuntimeException e) {
            permit.release();
            throw e;
        }
    }

    /**
     * 可领任务清单。「这个项目上有哪些活」—— 让执行者先看再领，而不是只能盲领。
     *
     * <p>{@code claimable=false} 的行照样列出来：平台全局并发已满是暂时的，执行者等一会儿再来
     * 比它以为「这个项目没活」然后走人要好。
     */
    public List<AgentTaskSummary> list(String projectCode, int limit) {
        List<Task> candidates = projectCode == null || projectCode.isBlank()
                ? taskRepository.findDispatchable(limit)
                : taskRepository.findDispatchableInProject(loadProjectByCode(projectCode).id(), limit);

        List<AgentTaskSummary> summaries = new ArrayList<>();
        boolean platformFull = !concurrencyGate.hasAvailableSlot();
        for (Task task : candidates) {
            String code = projectCodeOf(task);
            String reason = platformFull
                    ? "平台同时运行的任务已达到上限 " + concurrencyGate.limit()
                    : "";
            summaries.add(new AgentTaskSummary(task.getId().value(), code, task.getTitle(),
                    task.getStatus().name(), task.getPriority(), task.getSource().name(),
                    task.getCreatedAt(),
                    !platformFull, reason));
        }
        return summaries;
    }

    /** 当前设备明确受理但暂时无法执行时，把任务落到排队状态，不等于创建执行记录。 */
    public Task queueForDevice(long taskId) {
        Task task = loadTask(TaskId.of(taskId));
        if (task.getStatus() == TaskStatus.QUEUED) {
            return task;
        }
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new DomainException("任务当前不能进入排队：" + task.getStatus());
        }
        ProjectProfile project = loadProject(task);
        project.requireDispatchable();
        task.queue();
        taskRepository.save(task);
        return task;
    }

    /**
     * 心跳：续租 + 取回「现在该干什么」。
     *
     * <p>已收场的执行记录也允许心跳（只读）：外部执行者挂起等人批准时，它唯一能问的就是
     * 「批完了吗」。这条路径不再续租 —— 活已经交出去了，占着工作区没有意义。
     */
    public AgentRunSnapshot heartbeat(long runId, String agentName) {
        return heartbeat(runId, agentName, null);
    }

    public AgentRunSnapshot heartbeat(long runId, String agentName, String deviceId) {
        TaskRun run = loadRun(TaskRunId.of(runId));
        Task task = loadTask(run.getTaskId());
        Instant now = now();

        if (!run.getStatus().isFinished()) {
            if (!run.isLeaseHolder(agentName, deviceId)) {
                throw new DomainException("这条执行记录不是「" + agentName + "」认领的，不能续租：" + runId);
            }
            run.renewLease(agentName, deviceId, now.plus(lease), now);
            taskRunRepository.renewLease(
                    run.getId(), deviceId, run.getLeaseExpiresAt(), run.getLastHeartbeatAt());
        }
        return snapshot(run, task);
    }

    /**
     * 上报一次结果。结算与内部执行者完全同路：裁决就高不就低、阈值内自动放行并回一条续跑指令、
     * 超阈值挂起等人、做完收敛为终态。
     *
     * @param sessionId 可选：执行者的会话标识（codex 的 thread_id / 别家的 session id），
     *                  平台据此在批准之后还能把活交回同一个会话
     * @param tokens    可选：执行者自报的 token 用量；报不了就传零，不要编
     */
    public AgentReportOutcome report(long runId,
                                     String agentName,
                                     ExecReport report,
                                     String sessionId,
                                     TokenUsage tokens,
                                     String rawResult) {
        return report(runId, agentName, null, report, sessionId, tokens, rawResult);
    }

    public AgentReportOutcome report(long runId,
                                     String agentName,
                                     String deviceId,
                                     ExecReport report,
                                     String sessionId,
                                     TokenUsage tokens,
                                     String rawResult) {
        TaskRun run = loadRun(TaskRunId.of(runId));
        if (!run.isLeaseHolder(agentName, deviceId)) {
            throw new DomainException("这条执行记录不是「" + agentName + "」认领的，不能上报：" + runId);
        }
        if (run.getStatus().isFinished()) {
            throw new DomainException("这次尝试已经收场（" + run.getStatus() + "），不要再上报；"
                    + "要接着干请重新认领：" + runId);
        }
        Task task = loadTask(run.getTaskId());
        ProjectProfile project = loadProject(task);

        // 先把会话 ID 记下来再结算：挂起等人时这次会话很可能就结束了，
        // 批准之后要能把它捡回来，否则任务只能从头再跑一遍。
        settlement.adoptThreadId(task.getId(), sessionId, task.getCodexThreadId().orElse(null));
        markDeliveredAdjustments(run);

        ExecResult result = report.toResult(null, sessionId, tokens, rawResult);
        int seq = appendEvent(run, "agent.report", reportPayload(report, result));
        settlement.recordReportedBugs(task, result);

        if (!result.schemaValid()) {
            return settleAndRelease(run, result, seq);
        }
        if (result.status() == RunStatus.BLOCKED && settlement.stillRunning(task.getId())) {
            AgentReportOutcome outcome = decide(run, task, project, result, seq);
            if (outcome.outcome() != AgentReportOutcome.Outcome.CONTINUE) {
                releasePermit(run.getId());
            }
            return outcome;
        }
        return settleAndRelease(run, result, seq);
    }

    /**
     * 追加外部执行者的实时事件。事件只存原文，平台不解析 Codex 私有结构。
     */
    public int appendEvents(long runId, String agentName, List<AgentRunEvent> incoming) {
        return appendEvents(runId, agentName, null, incoming);
    }

    public int appendEvents(long runId,
                            String agentName,
                            String deviceId,
                            List<AgentRunEvent> incoming) {
        requireText(agentName, "执行者");
        if (incoming == null || incoming.isEmpty()) {
            return 0;
        }
        if (incoming.size() > 100) {
            throw new DomainException("单批事件不能超过 100 条");
        }
        TaskRun run = loadRun(TaskRunId.of(runId));
        if (!run.isLeaseHolder(agentName, deviceId)) {
            throw new DomainException("这条执行记录不是「" + agentName + "」认领的，不能写事件：" + runId);
        }
        if (run.getStatus().isFinished()) {
            throw new DomainException("这次执行已经结束，不能再写事件：" + runId);
        }
        int seq = runEventRepository.findByRun(run.getId()).size();
        int appended = 0;
        for (AgentRunEvent event : incoming) {
            if (event == null) {
                continue;
            }
            runEventRepository.append(run.getId(), new RunEvent(
                    seq++, event.type(), event.payload(), event.occurredAt()));
            appended++;
        }
        return appended;
    }

    /**
     * 回收失联的执行者：租约过期还没交活，这次尝试判失败并放开任务。
     *
     * <p>返回回收条数。单条处理失败只跳过它自己 —— 一条记录状态异常（比如刚好被人收场了）
     * 不该让后面排队的失联记录永远收不了场。
     */
    public int releaseExpiredLeases() {
        int released = 0;
        for (TaskRun run : taskRunRepository.findExpiredLeases(now())) {
            try {
                if (reap(run)) {
                    released++;
                }
            } catch (RuntimeException ignored) {
                // 跳过：下一拍还会看到它，而别让这一条把整轮回收拖停
            }
        }
        return released;
    }

    private boolean reap(TaskRun run) {
        if (run.getStatus().isFinished()) {
            return false;
        }
        String agentName = run.getAgentName() == null ? "未知执行者" : run.getAgentName();
        Instant expiredAt = run.getLeaseExpiresAt();

        settlement.finishAttempt(run, new ExecResult(RunStatus.FAILED, null, null, TokenUsage.ZERO,
                "执行者失联：租约在 " + expiredAt + " 到期未续（执行者：" + agentName + "）。"
                        + "工作区已经放开，可以直接重试。",
                "lease-expired", true, List.of(), List.of(), null));
        appendEvent(run, "platform.lease-expired", "{\"agent\":\"" + json(agentName) + "\"}");
        releasePermit(run.getId());
        return true;
    }

    /** 挂起等人，或者带着平台自己拍完的结论接着跑。 */
    private AgentReportOutcome decide(TaskRun run, Task task, ProjectProfile project, ExecResult result, int seq) {
        int rounds = decisionRounds(run.getId()) + 1;
        if (rounds > MAX_DECISION_ROUNDS) {
            return settle(run, ExecResult.invalidOutput(null, result.threadId(), result.tokens(),
                    "一次尝试内连续决策超过 " + MAX_DECISION_ROUNDS + " 轮，已停止以免无限循环",
                    result.rawResult()), seq);
        }
        RaisedDecision raised = settlement.raise(task, project, result);
        appendEvent(run, "platform.decision", RunSettlementService.decisionPayload(raised.decision()), seq);

        if (raised.needsHuman()) {
            run.finish(result);
            taskRunRepository.save(run);
            settlement.blockForHuman(task.getId(), raised.decision().getId());
            Task fresh = loadTask(task.getId());
            return new AgentReportOutcome(AgentReportOutcome.Outcome.BLOCKED, run.getStatus().name(),
                    fresh.getStatus().name(), null, raised.decision().getId().value(),
                    raised.decision().getEffectiveLevel().name(),
                    "问题已交给人：通过阈值或不可逆的改动必须有人拍板。你的这次尝试到此为止，"
                            + "可以用心跳等批复，也可以在批复后重新认领继续。",
                    MAX_DECISION_ROUNDS - rounds);
        }
        String instruction = withStandards(task.getId(), appendPendingAdjustments(
                run, settlement.continuationPrompt(raised.decision())));
        return new AgentReportOutcome(AgentReportOutcome.Outcome.CONTINUE, run.getStatus().name(),
                task.getStatus().name(), instruction,
                raised.decision().getId().value(), raised.decision().getEffectiveLevel().name(),
                "平台按分级规则替你定了：这一条落在阈值内且可逆。带着结论接着干，"
                        + "几次改动的分界线不是你能自己放宽的。",
                MAX_DECISION_ROUNDS - rounds);
    }

    /** 收场：写结论、按结果推进任务状态。 */
    private AgentReportOutcome settle(TaskRun run, ExecResult result, int seq) {
        RunSettlementService.AttemptFinish finish = settlement.finishAttempt(run, result);
        if (finish.deferredForAdjustment()) {
            appendEvent(run, "platform.adjustment-deferred",
                    "{\"reason\":\"success-reported-with-pending-adjustments\"}", seq);
            String instruction = withStandards(run.getTaskId(), appendPendingAdjustments(run,
                    "上一轮执行已结束，但平台收到了新的调整方向。请接着同一会话继续，不要重新开始。"));
            return new AgentReportOutcome(AgentReportOutcome.Outcome.CONTINUE,
                    RunStatus.RUNNING.name(),
                    finish.taskStatus().name(),
                    instruction,
                    null,
                    null,
                    "这次上报没有直接收场：任务执行期间又出现了调整方向，平台要求你先纳入调整再结束。",
                    MAX_DECISION_ROUNDS);
        }

        boolean done = run.getStatus() == RunStatus.SUCCESS;
        return new AgentReportOutcome(
                done ? AgentReportOutcome.Outcome.DONE : AgentReportOutcome.Outcome.FAILED,
                run.getStatus().name(),
                finish.taskStatus() == null ? TaskStatus.CANCELLED.name() : finish.taskStatus().name(),
                null,
                null,
                null,
                done ? completionMessage(run)
                        : "这次尝试判失败：" + (result.summary() == null ? "（执行器没有给出摘要）" : result.summary()),
                MAX_DECISION_ROUNDS);
    }

    private static String completionMessage(TaskRun run) {
        return run.getBranchName() == null
                ? "任务已完成。改动留在项目目录中，等人工验收。"
                : "任务已完成。改动留在分支 " + run.getBranchName() + " 上，等人工验收。";
    }

    private String withStandards(TaskId taskId, String instruction) {
        if (taskCodeStandardService == null) return instruction;
        String standards = taskCodeStandardService.context(taskId).promptSection();
        return standards.isBlank() ? instruction : instruction + standards;
    }

    private AgentReportOutcome settleAndRelease(TaskRun run, ExecResult result, int seq) {
        AgentReportOutcome outcome = settle(run, result, seq);
        if (outcome.outcome() != AgentReportOutcome.Outcome.CONTINUE) {
            releasePermit(run.getId());
        }
        return outcome;
    }

    private void releasePermit(TaskRunId runId) {
        deliveredAdjustments.remove(runId.value());
        ExecutionConcurrencyGate.Permit permit = activePermits.remove(runId.value());
        if (permit != null) {
            permit.release();
        }
    }

    private void restoreActiveLeases(Instant now) {
        for (TaskRun run : taskRunRepository.findActiveLeases(now)) {
            activePermits.put(run.getId().value(), concurrencyGate.restoreActive());
        }
    }

    private Optional<AgentWorkPackage> startAttempt(Task task,
                                                    ProjectProfile project,
                                                    String agentName,
                                                    String sessionId,
                                                    String deviceId,
                                                    Instant now) {
        EnvironmentContext environment = EnvironmentContext.resolve(
                environmentEntryRepository.findApplicableTo(project.ownerId(), project.id()));
        PluginContext plugins = PluginContext.from(taskPluginRepository.findByTask(task.getId()));

        // 指令必须在推进状态之前算：instructionFor 靠「任务有没有会话」判断这是首跑还是续跑，
        // 先把这次会话写进去，首跑就会被当成一次带着旧会话的重试。
        TaskRun previous = taskRunRepository.findLatestByTask(task.getId()).orElse(null);
        String instruction = settlement.instructionFor(task, previous);
        TaskCodeStandardService.CodeStandardContext standards = taskCodeStandardService == null
                ? TaskCodeStandardService.CodeStandardContext.empty()
                : taskCodeStandardService.context(task.getId());
        instruction += standards.promptSection();

        TaskStatus status = task.getStatus();
        if (status == TaskStatus.PENDING || status == TaskStatus.QUEUED) {
            project.requireDispatchable();
            BranchPreparation preparation = branchProvisioningService.prepare(task, project);
            if (!preparation.ready()) {
                taskRepository.save(task);
                return Optional.empty();
            }
        } else if (status == TaskStatus.RUNNING) {
            task.getBranchName().ifPresent(branch -> branchManager.switchTo(project.path(), branch));
            task.beginContinuation();
        } else {
            throw new DomainException("任务当前状态不可认领：" + task.getId() + "，状态：" + status);
        }
        if (task.getCodexThreadId().isEmpty()) {
            task.recordThreadId(sessionId);
        }
        taskRepository.save(task);

        String branch = task.getBranchName().orElse(null);
        String baseRef = branchManager.currentCommit(project.path()).orElse(null);
        TaskRun run = TaskRun.start(task.getId(), task.getAttemptCount(), branch, baseRef);
        run.claimBy(agentName, deviceId, now.plus(lease));
        taskRunRepository.save(run);
        appendEvent(run, "agent.claim", "{\"agent\":\"" + json(agentName) + "\",\"executor\":\"外部执行者\"}");
        if (!standards.isEmpty()) {
            appendEvent(run, "platform.code-standards", standards.auditPayload());
        }
        instruction = appendPendingAdjustments(run, instruction);

        List<AgentWorkPackage.Image> images = new ArrayList<>();
        for (TaskAttachment attachment : attachmentRepository.findByTask(task.getId())) {
            images.add(new AgentWorkPackage.Image(attachment.id().value(), attachment.filename(),
                    attachment.storagePath()));
        }
        List<TaskDocument> taskDocuments = documentRepository.findByTask(task.getId());
        List<AgentWorkPackage.Document> documentSnapshots = taskDocuments.stream()
                .map(document -> new AgentWorkPackage.Document(
                        document.id().value(),
                        document.filename(),
                        document.contentType(),
                        document.sizeBytes(),
                        document.storagePath()))
                .toList();
        List<AgentWorkPackage.EnvironmentVariable> variables = environment.variables().stream()
                .map(variable -> new AgentWorkPackage.EnvironmentVariable(
                        variable.key(), variable.value(), variable.scope(),
                        variable.tags(), variable.description()))
                .toList();
        List<AgentWorkPackage.Plugin> pluginSnapshots = plugins.plugins().stream()
                .map(plugin -> new AgentWorkPackage.Plugin(
                        plugin.pluginId().value(),
                        plugin.pluginName(),
                        plugin.revision(),
                        plugin.filename(),
                        plugin.contentHash(),
                        plugin.content()))
                .toList();
        return Optional.of(new AgentWorkPackage(PROTOCOL, task.getId().value(), run.getId().value(), run.getAttemptNo(),
                task.getTitle(), project.code(), project.path(), branch,
                instruction + plugins.referenceSection() + environment.promptSection()
                        + TaskDocumentPrompt.section(taskDocuments)
                        + "\n\n---\n\n" + contract.reportingRules(),
                task.getCodexThreadId().orElse(null), images, documentSnapshots,
                pluginSnapshots, variables,
                project.defaultDecisionLevel().name(), (int) lease.toSeconds(), run.getLeaseExpiresAt(),
                contractUrl));
    }

    private AgentRunSnapshot snapshot(TaskRun run, Task task) {
        Decision decision = relevantDecision(run, task);
        AgentRunSnapshot.NextAction action = nextAction(run, task, decision);
        return new AgentRunSnapshot(run.getId().value(), task.getId().value(), run.getStatus().name(),
                task.getStatus().name(), action, noteFor(action, decision),
                decision == null ? null : decision.getId().value(),
                decision == null ? null : decision.getStatus().name(),
                (int) lease.toSeconds(), run.getLeaseExpiresAt());
    }

    /** 心跳里该看的那张决策单：任务正阻塞在它上面，或者它是这个任务最近一次已决议的。 */
    private Decision relevantDecision(TaskRun run, Task task) {
        Optional<Decision> blocking = task.getBlockingDecisionId()
                .flatMap(decisionRepository::findById);
        if (blocking.isPresent()) {
            return blocking.get();
        }
        if (run.getStatus() == RunStatus.BLOCKED) {
            return decisionRepository.findLatestResolvedByTask(task.getId()).orElse(null);
        }
        return null;
    }

    private AgentRunSnapshot.NextAction nextAction(TaskRun run, Task task, Decision decision) {
        if (task.getStatus() == TaskStatus.CANCELLED) {
            return AgentRunSnapshot.NextAction.STOP;
        }
        if (!run.getStatus().isFinished()) {
            return AgentRunSnapshot.NextAction.KEEP_WORKING;
        }
        if (run.getStatus() == RunStatus.BLOCKED && decision != null
                && decision.getStatus() == DecisionStatus.PENDING) {
            return AgentRunSnapshot.NextAction.WAIT_DECISION;
        }
        if (run.getStatus() == RunStatus.BLOCKED && decision != null) {
            return decision.getStatus() == DecisionStatus.REJECTED
                    ? AgentRunSnapshot.NextAction.STOP
                    : AgentRunSnapshot.NextAction.CLAIM_AGAIN;
        }
        return claimable(task)
                ? AgentRunSnapshot.NextAction.CLAIM_AGAIN
                : AgentRunSnapshot.NextAction.STOP;
    }

    private static String noteFor(AgentRunSnapshot.NextAction action, Decision decision) {
        return switch (action) {
            case KEEP_WORKING -> "活还是你的，接着干；记得按时心跳，租约一断平台就按失联处理。";
            case WAIT_DECISION -> "问题已经交给人了，停手等着。批完重新认领就能拿到结论。";
            case CLAIM_AGAIN -> decision != null
                    ? "决策已经归位（" + decision.getStatus() + "），重新认领就能拿到带结论的续跑指令。"
                    : "任务回到了可执行状态，重新认领即可。";
            case STOP -> "任务已经结束或被人终止了，别再动它。";
        };
    }

    /** 不限项目时自己挑一条；候选必须再次锁行确认，SQL 读到的状态不能当作认领凭据。 */
    private Task lockCandidate() {
        for (Task candidate : taskRepository.findDispatchable(CANDIDATE_SCAN)) {
            Task locked = taskRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (locked != null && claimable(locked)) {
                return locked;
            }
        }
        return null;
    }

    /** 指定项目时挑一条；项目里此刻没活就返回空。 */
    private Task lockCandidateIn(ProjectProfile project) {
        for (Task candidate : taskRepository.findDispatchableInProject(project.id(), CANDIDATE_SCAN)) {
            Task locked = taskRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (locked != null && claimable(locked)) {
                return locked;
            }
        }
        return null;
    }

    /**
     * 接单前的最小执行环境检查。项目目录是平台能独立判断的事实；设备 Codex 状态来自最近心跳，
     * 设备必须先在 TTL 内报过在线，检查失败时不创建 task_run、不消耗执行位。
     */
    private ProjectProfile preflight(Task task, String deviceId, Instant now) {
        ProjectProfile project = loadProject(task);
        if (taskCodeStandardService != null) {
            taskCodeStandardService.requireReady(task.getId());
        }
        project.requireDispatchable();
        if (!Files.isDirectory(Path.of(project.path()))) {
            throw new DomainException("项目目录不存在或不可访问：" + project.path()
                    + "，请先在项目设置中重新选择目录");
        }
        if (deviceId == null || deviceId.isBlank()) {
            return project;
        }
        ExecutionDevice device = executionDeviceRepository.findByDeviceId(deviceId.trim())
                .orElseThrow(() -> new DomainException(
                        "设备尚未注册心跳：" + deviceId + "，请重新连接设备代理"));
        if (!device.isOnline(now, DEVICE_ONLINE_TTL)) {
            throw new DomainException("设备已离线：" + device.hostname()
                    + "，请重新连接或更换设备");
        }
        if (!device.codex().available()) {
            throw new DomainException("设备「" + device.hostname() + "」上的 Codex 不可用："
                    + device.codex().message() + "，请修复 Codex 安装或路径后重试");
        }
        return project;
    }

    private void markDeviceClaimed(String deviceId, Instant claimedAt) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        executionDeviceRepository.findByDeviceId(deviceId.trim())
                .map(device -> device.markClaimed(claimedAt))
                .ifPresent(executionDeviceRepository::save);
    }

    private ProjectProfile loadProjectByCode(String projectCode) {
        return projectProfileRepository.findByCode(projectCode)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectCode));
    }

    /** 清单里要回项目标识，而候选行只有 projectId —— 按 ID 查一次画像，顺带把「项目没了」变成一句人话。 */
    private String projectCodeOf(Task task) {
        return projectProfileRepository.findById(task.getProjectId())
                .map(ProjectProfile::code)
                .orElse("");
    }

    /** 本次尝试里平台已经替执行者做过几轮决定 —— 上限按它算，而不是按内存里数不清的调用次数。 */
    private int decisionRounds(TaskRunId runId) {
        return (int) runEventRepository.findByRun(runId).stream()
                .filter(event -> "platform.decision".equals(event.type()))
                .count();
    }

    private int appendEvent(TaskRun run, String type, String payload) {
        return appendEvent(run, type, payload, runEventRepository.findByRun(run.getId()).size());
    }

    /**
     * 事件是旁路记录：写不进去只影响复盘，绝不能因为写日志失败把一次上报打断
     * （与内部执行者的取舍一致）。返回下一条事件的序号。
     */
    private int appendEvent(TaskRun run, String type, String payload, int seq) {
        try {
            runEventRepository.append(run.getId(), new RunEvent(seq, type, payload, now()));
        } catch (RuntimeException ignored) {
            // 见方法注释
        }
        return seq + 1;
    }

    /**
     * 把待调整拼进本次指令，并记住它已经随这条租约下发。
     * 只有下一次上报时才标记 APPLIED，这样执行期间新加的调整不会被误算进上一轮。
     */
    private String appendPendingAdjustments(TaskRun run, String instruction) {
        Optional<RunSettlementService.AdjustmentInstruction> pending =
                settlement.pendingAdjustmentInstruction(run.getTaskId());
        if (pending.isEmpty()) {
            return instruction;
        }
        RunSettlementService.AdjustmentInstruction adjustment = pending.orElseThrow();
        deliveredAdjustments.put(run.getId().value(), adjustment.ids());
        appendEvent(run, "platform.adjustment", adjustmentPayload(adjustment));
        return instruction + "\n\n---\n\n" + adjustment.prompt();
    }

    private void markDeliveredAdjustments(TaskRun run) {
        List<TaskAdjustmentId> delivered = deliveredAdjustments.remove(run.getId().value());
        settlement.markAdjustmentsApplied(delivered, run.getId());
    }

    private static String adjustmentPayload(RunSettlementService.AdjustmentInstruction adjustment) {
        return "{\"adjustmentIds\":["
                + adjustment.ids().stream()
                .map(id -> Long.toString(id.value()))
                .collect(java.util.stream.Collectors.joining(","))
                + "]}";
    }

    private static String reportPayload(ExecReport report, ExecResult result) {
        String status = report.status() == null ? "" : json(report.status());
        return "{\"status\":\"" + status + "\",\"changed\":" + result.changedFiles().size() + "}";
    }

    private Task loadTask(TaskId taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
    }

    private TaskRun loadRun(TaskRunId runId) {
        return taskRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundException("执行记录不存在：" + runId));
    }

    private ProjectProfile loadProject(Task task) {
        return projectProfileRepository.findById(task.getProjectId())
                .orElseThrow(() -> new EntityNotFoundException("任务所属项目不存在：" + task.getProjectId()));
    }

    /** 执行者名字会进事件流的 JSON 列，这里只做最小转义：引号与反斜杠，再去掉换行。 */
    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("[\\r\\n]", " ");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
    }

    private boolean claimable(Task task) {
        if (task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.QUEUED) {
            return true;
        }
        if (task.getStatus() != TaskStatus.RUNNING || task.getCodexThreadId().isEmpty()) {
            return false;
        }
        return taskRunRepository.findLatestByTask(task.getId())
                .map(run -> run.getStatus() == RunStatus.BLOCKED)
                .orElse(false);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
