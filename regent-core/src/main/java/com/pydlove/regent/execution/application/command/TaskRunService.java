package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskAttachment;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.execution.domain.port.RunEventListener;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.execution.domain.repository.TaskAttachmentRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.governance.application.command.RaisedDecision;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.plugin.application.query.PluginContext;
import com.pydlove.regent.plugin.domain.repository.TaskPluginRepository;
import com.pydlove.regent.project.application.query.EnvironmentContext;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 单任务执行编排 —— 把「一个已认领的任务」跑到出结论为止。
 *
 * <p>一次调用对应一次尝试（{@code task_run} 的一行）。中间的过程是：
 *
 * <pre>
 *   codex exec ──成功──▶ 任务 DONE
 *        │
 *        ├──失败／超时──▶ 任务 FAILED
 *        │
 *        └──要决策──▶ 仲裁「就高不就低」
 *                        │
 *                        ├──落在阈值内且可逆──▶ AI 自主决断留痕 ──▶ 带着结论立刻续跑（同一行记录内循环）
 *                        └──超出阈值或不可逆──▶ 决策单待批、任务 BLOCKED，本次尝试结束
 * </pre>
 *
 * <h2>它只管「把进程拉起来并读它的出参」</h2>
 * 出参读进来之后怎么收场（裁决、指令文案、任务状态迁移）全在 {@link RunSettlementService}，
 * 因为外部执行者走的 HTTP 那条路要用同一套判定。这里的原因很直接：治理只允许有一份实现。
 *
 * <h2>为什么结论一律落在库里，而不是抛给调用方</h2>
 * 这个方法跑在工作线程上，异常没有调用方可以接 —— 抛出去只会变成一条没人看的堆栈，
 * 而任务会永远停在 RUNNING 上，工作台上出现一条「跑了三天还没动静」的记录。
 * 所以失败也要成为一份写下来的事实：执行记录 FAILED + 任务 FAILED + 摘要写明原因。
 */
public class TaskRunService {

    /**
     * 一次尝试内允许的连续决策轮次上限。
     *
     * <p>自动决断的本意是「别为了改个文案来问我」，不是「让你自己跟自己聊到天亮」。
     * 触到上限说明这个任务的问题不在一两轮决策之内，停下来让人看一眼比继续烧 token 划算。
     */
    public static final int MAX_DECISION_ROUNDS = 5;

    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final RunEventRepository runEventRepository;
    private final TaskAttachmentRepository attachmentRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final EnvironmentEntryRepository environmentEntryRepository;
    private final TaskPluginRepository taskPluginRepository;
    private final RunSettlementService settlement;
    private final AgentExecutor agentExecutor;
    private final Duration timeout;

    public TaskRunService(TaskRepository taskRepository,
                          TaskRunRepository taskRunRepository,
                          RunEventRepository runEventRepository,
                          TaskAttachmentRepository attachmentRepository,
                          ProjectProfileRepository projectProfileRepository,
                          EnvironmentEntryRepository environmentEntryRepository,
                          TaskPluginRepository taskPluginRepository,
                          RunSettlementService settlement,
                          AgentExecutor agentExecutor,
                          Duration timeout) {
        this.taskRepository = taskRepository;
        this.taskRunRepository = taskRunRepository;
        this.runEventRepository = runEventRepository;
        this.attachmentRepository = attachmentRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.environmentEntryRepository = environmentEntryRepository;
        this.taskPluginRepository = taskPluginRepository;
        this.settlement = settlement;
        this.agentExecutor = agentExecutor;
        this.timeout = timeout;
    }

    /**
     * 执行一次尝试。返回的执行记录已经收敛（含被兜底判为失败的情况）。
     */
    public TaskRun execute(TaskId taskId) {
        Execution execution = startExecution(taskId);

        try {
            return executeRounds(execution);
        } catch (RuntimeException e) {
            failRun(execution.run, execution.recorder, e);
            settlement.finishTask(taskId, RunStatus.FAILED);
            return execution.run;
        }
    }

    /** 准备本次尝试的上下文。上一轮执行记录必须在本次记录落库前读取。 */
    private Execution startExecution(TaskId taskId) {
        Task task = loadTask(taskId);
        ProjectProfile project = projectProfileRepository.findById(task.getProjectId())
                .orElseThrow(() -> new EntityNotFoundException("任务所属项目不存在：" + task.getProjectId()));
        String branch = task.getBranchName()
                .orElseThrow(() -> new DomainException("任务没有分支名，无法执行：" + taskId));

        TaskRun previous = taskRunRepository.findLatestByTask(taskId).orElse(null);

        TaskRun run = TaskRun.start(taskId, task.getAttemptCount(), branch);
        taskRunRepository.save(run);
        EventRecorder recorder = new EventRecorder(runEventRepository, run.getId());
        PluginContext plugins = PluginContext.from(taskPluginRepository.findByTask(taskId));
        if (!plugins.isEmpty()) {
            recorder.record("platform.plugins", plugins.auditPayload());
        }

        String threadId = task.getCodexThreadId().orElse(null);
        String prompt = settlement.instructionFor(task, previous);
        List<String> images = attachmentRepository.findByTask(taskId).stream()
                .map(TaskAttachment::storagePath)
                .toList();
        EnvironmentContext environment = EnvironmentContext.resolve(
                environmentEntryRepository.findApplicableTo(project.id()));
        return new Execution(task, project, run, recorder, threadId, prompt, images, plugins, environment);
    }

    private TaskRun executeRounds(Execution execution) {
        for (int round = 1; round <= MAX_DECISION_ROUNDS; round++) {
            Optional<TaskRun> finished = executeRound(execution);
            if (finished.isPresent()) {
                return finished.get();
            }
        }
        throw new DomainException(
                "一次尝试内连续决策超过 " + MAX_DECISION_ROUNDS + " 轮，已停止以免无限循环");
    }

    private Optional<TaskRun> executeRound(Execution execution) {
        ExecResult result = agentExecutor.execute(
                new ExecRequest(
                        execution.task.getProjectId(),
                        execution.project.path(),
                        execution.prompt + execution.plugins.promptSection() + execution.environment.promptSection(),
                        execution.threadId,
                        timeout,
                        execution.images,
                        execution.environment.values(),
                        execution.run.getBranchName()),
                execution.recorder);
        execution.threadId = settlement.adoptThreadId(
                execution.task.getId(), result.threadId(), execution.threadId);
        settlement.recordReportedBugs(execution.task, result);

        if (result.status() == RunStatus.BLOCKED && settlement.stillRunning(execution.task.getId())) {
            return handleBlockedResult(execution, result);
        }
        return Optional.of(finishTerminal(execution, result));
    }

    private Optional<TaskRun> handleBlockedResult(Execution execution, ExecResult result) {
        RaisedDecision raised = settlement.raise(execution.task, execution.project, result);
        execution.recorder.record("platform.decision", RunSettlementService.decisionPayload(raised.decision()));

        if (raised.needsHuman()) {
            return Optional.of(blockForHuman(execution, result, raised.decision().getId()));
        }
        execution.prompt = settlement.continuationPrompt(raised.decision());
        return Optional.empty();
    }

    private TaskRun blockForHuman(Execution execution, ExecResult result, DecisionId decisionId) {
        execution.run.finish(result);
        taskRunRepository.save(execution.run);
        settlement.blockForHuman(execution.task.getId(), decisionId);
        return execution.run;
    }

    private TaskRun finishTerminal(Execution execution, ExecResult result) {
        execution.run.finish(result);
        taskRunRepository.save(execution.run);
        if (result.status() == RunStatus.INTERRUPTED) {
            // 平台关闭打断了进程，不是任务业务失败。任务留在 RUNNING，
            // 由下次启动的恢复服务放回队列并续跑。
            return execution.run;
        }
        settlement.finishTask(execution.task.getId(), execution.run.getStatus());
        return execution.run;
    }

    private void failRun(TaskRun run, EventRecorder recorder, RuntimeException cause) {
        if (run.getStatus().isFinished()) {
            return;
        }
        recorder.record("platform.error", errorPayload(cause));
        run.finish(new ExecResult(RunStatus.FAILED, null, null, TokenUsage.ZERO,
                "执行编排异常：" + cause.getMessage(), cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                true, List.of(), List.of(), null));
        taskRunRepository.save(run);
    }

    private Task loadTask(TaskId taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
    }

    /** 事件是旁路记录：写不进去只影响复盘，绝不能因为写日志失败把执行打断。 */
    private static final class EventRecorder implements RunEventListener {

        private final RunEventRepository repository;
        private final TaskRunId runId;
        private int seq;

        private EventRecorder(RunEventRepository repository, TaskRunId runId) {
            this.repository = repository;
            this.runId = runId;
        }

        @Override
        public void onEvent(String type, String payloadJson) {
            record(type, payloadJson);
        }

        private void record(String type, String payloadJson) {
            try {
                repository.append(runId, new RunEvent(seq, type, payloadJson, now()));
            } catch (RuntimeException ignored) {
                // 吞掉：写不进去也不该打断执行。序号照常前进，否则后面每条都会撞同一个唯一键
            } finally {
                seq++;
            }
        }
    }

    private static String errorPayload(RuntimeException cause) {
        return "{\"error\":\"" + cause.getClass().getSimpleName() + "\"}";
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static final class Execution {

        private final Task task;
        private final ProjectProfile project;
        private final TaskRun run;
        private final EventRecorder recorder;
        /** 任务附件。每次尝试开局读一次就固定下来：跑的过程中没人能再往这个任务上贴图。 */
        private final List<String> images;
        private final PluginContext plugins;
        private final EnvironmentContext environment;
        private String threadId;
        private String prompt;

        private Execution(Task task,
                          ProjectProfile project,
                          TaskRun run,
                          EventRecorder recorder,
                          String threadId,
                          String prompt,
                          List<String> images,
                          PluginContext plugins,
                          EnvironmentContext environment) {
            this.task = task;
            this.project = project;
            this.run = run;
            this.recorder = recorder;
            this.threadId = threadId;
            this.prompt = prompt;
            this.images = images;
            this.plugins = plugins;
            this.environment = environment;
        }
    }
}
