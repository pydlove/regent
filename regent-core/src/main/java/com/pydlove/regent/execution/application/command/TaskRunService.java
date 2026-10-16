package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.TaskAttachment;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.execution.domain.port.RunEventListener;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.execution.domain.repository.TaskAttachmentRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.governance.application.command.DecisionRaiseService;
import com.pydlove.regent.governance.application.command.RaiseDecisionCommand;
import com.pydlove.regent.governance.application.command.RaisedDecision;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.governance.domain.model.ResolvedBy;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
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
 * <h2>为什么结论一律落在库里，而不是抛给调用方</h2>
 * 这个方法跑在工作线程上，异常没有调用方可以接 —— 抛出去只会变成一条没人看的堆栈，
 * 而任务会永远停在 RUNNING 上，看板上出现一条「跑了三天还没动静」的记录。
 * 所以失败也要成为一份写下来的事实：执行记录 FAILED + 任务 FAILED + 摘要写明原因。
 *
 * <h2>为什么要重新加载任务再改状态</h2>
 * 一次执行可能持续几十分钟，期间人可能在界面上点了「终止」。手里那份 {@code Task} 是开局时读的，
 * 直接拿它 complete/fail 会把人工终止悄悄覆盖回去。所以每次状态迁移前重新读一次，
 * 只在任务仍是 RUNNING 时才动手 —— 人的决定优先于执行结果。
 */
public class TaskRunService {

    /**
     * 一次尝试内允许的连续决策轮次上限。
     *
     * <p>自动决断的本意是「别为了改个文案来问我」，不是「让你自己跟自己聊到天亮」。
     * 触到上限说明这个任务的问题不在一两轮决策之内，停下来让人看一眼比继续烧 token 划算。
     */
    public static final int MAX_DECISION_ROUNDS = 5;

    private static final String RETRY_INSTRUCTION =
            "平台重试：上一轮执行没有跑完（原因见上一轮最后的说明）。请接着上一轮的进展继续完成任务，"
                    + "不要重复已经做好的部分，并按约定再次上报。";

    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final RunEventRepository runEventRepository;
    private final TaskAttachmentRepository attachmentRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final DecisionRepository decisionRepository;
    private final DecisionRaiseService decisionRaiseService;
    private final AgentExecutor agentExecutor;
    private final Duration timeout;

    public TaskRunService(TaskRepository taskRepository,
                          TaskRunRepository taskRunRepository,
                          RunEventRepository runEventRepository,
                          TaskAttachmentRepository attachmentRepository,
                          ProjectProfileRepository projectProfileRepository,
                          DecisionRepository decisionRepository,
                          DecisionRaiseService decisionRaiseService,
                          AgentExecutor agentExecutor,
                          Duration timeout) {
        this.taskRepository = taskRepository;
        this.taskRunRepository = taskRunRepository;
        this.runEventRepository = runEventRepository;
        this.attachmentRepository = attachmentRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.decisionRepository = decisionRepository;
        this.decisionRaiseService = decisionRaiseService;
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
            finishTask(taskId, RunStatus.FAILED);
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

        String threadId = task.getCodexThreadId().orElse(null);
        String prompt = openingPrompt(task, previous);
        List<String> images = attachmentRepository.findByTask(taskId).stream()
                .map(TaskAttachment::storagePath)
                .toList();
        return new Execution(task, project, run, recorder, threadId, prompt, images);
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
                        execution.prompt,
                        execution.threadId,
                        timeout,
                        execution.images),
                execution.recorder);
        execution.threadId = adoptThreadId(execution.task.getId(), result.threadId(), execution.threadId);

        if (result.status() == RunStatus.BLOCKED && stillRunning(execution.task.getId())) {
            return handleBlockedResult(execution, result);
        }
        return Optional.of(finishTerminal(execution, result));
    }

    private Optional<TaskRun> handleBlockedResult(Execution execution, ExecResult result) {
        RaisedDecision raised = decisionRaiseService.raise(
                raiseCommand(execution.task, execution.project, result));
        execution.recorder.record("platform.decision", decisionPayload(raised.decision()));

        if (raised.needsHuman()) {
            return Optional.of(blockForHuman(execution, result, raised.decision().getId()));
        }
        execution.prompt = continuationPrompt(raised.decision());
        return Optional.empty();
    }

    private TaskRun blockForHuman(Execution execution, ExecResult result, DecisionId decisionId) {
        execution.run.finish(result);
        taskRunRepository.save(execution.run);
        blockTask(execution.task.getId(), decisionId);
        return execution.run;
    }

    private TaskRun finishTerminal(Execution execution, ExecResult result) {
        execution.run.finish(result);
        taskRunRepository.save(execution.run);
        finishTask(execution.task.getId(), execution.run.getStatus());
        return execution.run;
    }

    /**
     * 本次尝试的第一条指令。
     *
     * <p>三种入口，靠「有没有会话」与「上一次尝试是怎么结束的」区分，而不是靠调用方传参：
     * 参数会传错，而库里的状态不会说谎。
     *
     * <p>{@code previous} 由调用方在插入本次记录之前读出并传进来，理由见 {@code startExecution} 里的注释。
     */
    private String openingPrompt(Task task, TaskRun previous) {
        if (task.getCodexThreadId().isEmpty()) {
            return task.getPrompt();
        }
        if (previous == null || previous.getStatus() != RunStatus.BLOCKED) {
            return RETRY_INSTRUCTION;
        }
        Decision decision = decisionRepository.findLatestResolvedByTask(task.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "任务停在待续跑上，但找不到已决议的决策：" + task.getId()));
        return continuationPrompt(decision);
    }

    /** 把决策结论翻译成执行器看得懂的指令。措辞里保留「谁拍的板」，模型据此知道这是终局结论。 */
    private static String continuationPrompt(Decision decision) {
        String resolver = decision.getResolvedBy()
                .map(by -> by == ResolvedBy.HUMAN ? "人工" : "平台按分级规则")
                .orElse("平台");
        return "决策已归位：上一轮你提出「" + decision.getQuestion() + "」，"
                + resolver + "裁决为：" + decision.getResolvedChoice().orElse("（无具体选项）")
                + "。理由：" + decision.getResolutionNote().orElse("（未填写）")
                + "\n\n请按此结论继续完成任务，并按约定再次上报。";
    }

    private RaiseDecisionCommand raiseCommand(Task task, ProjectProfile project, ExecResult result) {
        return new RaiseDecisionCommand(
                task.getId(),
                task.getProjectId(),
                project.defaultDecisionLevel(),
                result.decision().level(),
                result.decision().question(),
                result.decision().options(),
                result.decision().recommendation(),
                result.decision().reversible(),
                result.decision().blastRadius(),
                result.changedFiles());
    }

    /** 回填会话 ID。同一会话重复回报时不写库，避免一次执行刷出十几条空 UPDATE。 */
    private String adoptThreadId(TaskId taskId, String reported, String current) {
        if (reported == null || reported.isBlank()) {
            return current;
        }
        Task fresh = taskRepository.findById(taskId).orElse(null);
        if (fresh == null || fresh.getStatus() != TaskStatus.RUNNING) {
            return reported;
        }
        if (fresh.getCodexThreadId().filter(reported::equals).isPresent()) {
            return reported;
        }
        fresh.recordThreadId(reported);
        taskRepository.save(fresh);
        return reported;
    }

    private boolean stillRunning(TaskId taskId) {
        return taskRepository.findById(taskId)
                .map(task -> task.getStatus() == TaskStatus.RUNNING)
                .orElse(false);
    }

    private void blockTask(TaskId taskId, DecisionId decisionId) {
        Task fresh = loadTask(taskId);
        if (fresh.getStatus() != TaskStatus.RUNNING) {
            return;
        }
        fresh.block(decisionId);
        taskRepository.save(fresh);
    }

    private void finishTask(TaskId taskId, RunStatus outcome) {
        Task fresh = taskRepository.findById(taskId).orElse(null);
        if (fresh == null || fresh.getStatus() != TaskStatus.RUNNING) {
            return;
        }
        if (outcome == RunStatus.SUCCESS) {
            fresh.complete();
        } else {
            fresh.fail();
        }
        taskRepository.save(fresh);
    }

    private void failRun(TaskRun run, EventRecorder recorder, RuntimeException cause) {
        if (run.getStatus().isFinished()) {
            return;
        }
        recorder.record("platform.error", errorPayload(cause));
        run.finish(new ExecResult(RunStatus.FAILED, null, null, TokenUsage.ZERO,
                "执行编排异常：" + cause.getMessage(), cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                true, List.of(), null));
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

    /**
     * 平台事件也是 JSON —— {@code run_event.payload} 是 JSON 列，写不进非 JSON 文本。
     * 这里只拼 ID 与枚举名，不含用户文本，因此不需要转义。
     */
    private static String decisionPayload(Decision decision) {
        return "{\"decisionId\":" + decision.getId()
                + ",\"status\":\"" + decision.getStatus()
                + "\",\"effectiveLevel\":\"" + decision.getEffectiveLevel()
                + "\"}";
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
        private String threadId;
        private String prompt;

        private Execution(Task task,
                          ProjectProfile project,
                          TaskRun run,
                          EventRecorder recorder,
                          String threadId,
                          String prompt,
                          List<String> images) {
            this.task = task;
            this.project = project;
            this.run = run;
            this.recorder = recorder;
            this.threadId = threadId;
            this.prompt = prompt;
            this.images = images;
        }
    }
}
