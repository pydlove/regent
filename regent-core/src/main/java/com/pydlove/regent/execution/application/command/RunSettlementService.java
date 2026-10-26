package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.model.BugStatus;
import com.pydlove.regent.bug.domain.repository.BugRepository;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.ReportedBug;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskAdjustment;
import com.pydlove.regent.execution.domain.model.TaskAdjustmentId;
import com.pydlove.regent.execution.domain.model.TaskAdjustmentSource;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.model.TaskSource;
import com.pydlove.regent.execution.domain.repository.TaskAdjustmentRepository;
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
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewTaskRunId;
import com.pydlove.regent.review.domain.port.ReviewSnapshotScheduler;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.testing.domain.port.TestCaseExecutionScheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 一次尝试的编排大脑 —— 「开局说什么」与「收场怎么算」。
 *
 * <p>两条执行路径共用它：平台自己拉起的 {@code codex exec}（{@link TaskRunService}），
 * 以及外部执行者（{@code AgentRunService} 的认领 / 上报）。共用不是为了少写几行，
 * 而是为了让它们**在同一处**回答下面这四个问题 —— 这些问题答错一条就是治理事故：
 *
 * <ol>
 *   <li>这一次该让执行器读什么（首次需求 / 重试 / 带着人的结论续跑）；</li>
 *   <li>执行器报上来的决策该不该惊动人（就高不就低 + 项目阈值 + 可逆性）；</li>
 *   <li>人的结论怎么翻译回给执行器（保留「谁拍的板」）；</li>
 *   <li>任务状态什么时候跟着动（且只在它仍是 RUNNING 时才动 —— 人的终止优先）。</li>
 * </ol>
 *
 * <p>它不认识进程、不认识 JSON、不认识 HTTP，因此放在 application 层。终局收敛把执行记录、
 * 任务状态与待调整检查放在同一个事务里，避免成功结果刚写完就与新到的调整擦肩而过。
 */
public class RunSettlementService {

    private static final String RETRY_INSTRUCTION =
            "平台重试：上一轮执行没有跑完（原因见上一轮最后的说明）。请接着上一轮的进展继续完成任务，"
                    + "不要重复已经做好的部分，并按约定再次上报。";

    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final TaskAdjustmentRepository adjustmentRepository;
    private final DecisionRepository decisionRepository;
    private final DecisionRaiseService decisionRaiseService;
    private final BugRepository bugRepository;
    private final TestCaseExecutionScheduler testCaseExecutionScheduler;
    private final ReviewSnapshotScheduler reviewSnapshotScheduler;
    private final TransactionRunner transactionRunner;

    public RunSettlementService(TaskRepository taskRepository,
                                TaskRunRepository taskRunRepository,
                                TaskAdjustmentRepository adjustmentRepository,
                                DecisionRepository decisionRepository,
                                DecisionRaiseService decisionRaiseService,
                                BugRepository bugRepository,
                                TestCaseExecutionScheduler testCaseExecutionScheduler,
                                TransactionRunner transactionRunner) {
        this(taskRepository, taskRunRepository, adjustmentRepository, decisionRepository,
                decisionRaiseService, bugRepository, testCaseExecutionScheduler,
                (taskId, runId) -> {
                }, transactionRunner);
    }

    public RunSettlementService(TaskRepository taskRepository,
                                TaskRunRepository taskRunRepository,
                                TaskAdjustmentRepository adjustmentRepository,
                                DecisionRepository decisionRepository,
                                DecisionRaiseService decisionRaiseService,
                                BugRepository bugRepository,
                                TestCaseExecutionScheduler testCaseExecutionScheduler,
                                ReviewSnapshotScheduler reviewSnapshotScheduler,
                                TransactionRunner transactionRunner) {
        this.taskRepository = taskRepository;
        this.taskRunRepository = taskRunRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.decisionRepository = decisionRepository;
        this.decisionRaiseService = decisionRaiseService;
        this.bugRepository = bugRepository;
        this.testCaseExecutionScheduler = testCaseExecutionScheduler;
        this.reviewSnapshotScheduler = reviewSnapshotScheduler;
        this.transactionRunner = transactionRunner;
    }

    /**
     * 本次尝试的第一条指令。
     *
     * <p>三种入口靠「有没有会话」与「上一次尝试是怎么结束的」区分，而不是靠调用方传参：
     * 参数会传错，而库里的状态不会说谎。
     *
     * <p>{@code previous} 必须由调用方在插入本次记录之前读出并传进来：落行之后再查「上一次」，
     * 读到的永远是自己这一行 RUNNING，人工结论就再也进不了续跑指令。
     */
    public String instructionFor(Task task, TaskRun previous) {
        if (task.getCodexThreadId().isEmpty()) {
            return task.getPrompt();
        }
        if (task.isContinuation() && previous == null) {
            return "这是同一 Codex 会话中的后续任务，请沿用已有上下文，不要重新开始。\n\n"
                    + task.getPrompt();
        }
        if (previous == null || previous.getStatus() != RunStatus.BLOCKED) {
            return retryInstruction(task, previous);
        }
        Decision decision = decisionRepository.findLatestResolvedByTask(task.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "任务停在待续跑上，但找不到已决议的决策：" + task.getId()));
        return continuationPrompt(decision);
    }

    /**
     * 重试指令必须能独立成立：某些执行器在恢复旧会话时会因上下文不兼容而拒绝请求，
     * 此时平台需要无缝切到新会话，不能只给一句“接着上一轮”却让模型看不到任务要求。
     */
    private String retryInstruction(Task task, TaskRun previous) {
        StringBuilder prompt = new StringBuilder(RETRY_INSTRUCTION)
                .append("\n\n## 当前任务要求\n")
                .append(task.getPrompt());
        if (task.isContinuation()) {
            task.getContinuedFromTaskId()
                    .flatMap(taskRepository::findById)
                    .ifPresent(parent -> prompt.append("\n\n## 来源任务要求\n")
                            .append(parent.getTitle()).append('\n')
                            .append(parent.getPrompt()));
        }
        if (previous != null && previous.getSummary() != null && !previous.getSummary().isBlank()) {
            prompt.append("\n\n## 上一轮结果\n").append(previous.getSummary());
        }
        return prompt.toString();
    }

    /** 把决策结论翻译成执行器看得懂的指令。措辞里保留「谁拍的板」，模型据此知道这是终局结论。 */
    public String continuationPrompt(Decision decision) {
        String resolver = decision.getResolvedBy()
                .map(by -> by == ResolvedBy.HUMAN ? "人工" : "平台按分级规则")
                .orElse("平台");
        return "决策已归位：上一轮你提出「" + decision.getQuestion() + "」，"
                + resolver + "裁决为：" + decision.getResolvedChoice().orElse("（无具体选项）")
                + "。理由：" + decision.getResolutionNote().orElse("（未填写）")
                + "\n\n请按此结论继续完成任务，并按约定再次上报。";
    }

    /**
     * 取当前待下发的调整方向。这里只生成指令，不改状态：调用方真正把指令交给执行器之后再
     * 标记 APPLIED；执行器没被拉起时，调整仍留在待下发队列。
     */
    public Optional<AdjustmentInstruction> pendingAdjustmentInstruction(TaskId taskId) {
        List<TaskAdjustment> pending = adjustmentRepository.findPendingByTask(taskId);
        if (pending.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder prompt = new StringBuilder("任务执行期间新增了以下调整方向，必须在继续前纳入：\n");
        int index = 1;
        for (TaskAdjustment adjustment : pending) {
            prompt.append('\n').append(index++).append(". ");
            if (adjustment.getSource() == TaskAdjustmentSource.TASK) {
                prompt.append("来自任务 #")
                        .append(adjustment.getSourceTaskId().orElseThrow().value())
                        .append("「")
                        .append(adjustment.getSourceTitle().orElse("未命名任务"))
                        .append("」：\n");
            } else {
                prompt.append("临时补充：\n");
            }
            prompt.append(adjustment.getContent()).append('\n');
        }
        prompt.append("\n请先检查上一轮已经完成的改动，不要推倒重做；按这些新方向补齐实现与验证。\n")
                .append("如果新方向带来新的风险或不可逆操作，仍按平台上报约定提出决策，不能自行放宽等级。\n")
                .append("处理完成后按约定再次上报。");
        return Optional.of(new AdjustmentInstruction(
                pending.stream().map(TaskAdjustment::getId).toList(),
                prompt.toString()));
    }

    /** 执行器确实收到指令后调用；记录是哪次尝试下发的。 */
    public void markAdjustmentsApplied(List<TaskAdjustmentId> ids, TaskRunId runId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        transactionRunner.inTransaction(() -> {
            Instant appliedAt = now();
            for (TaskAdjustmentId id : ids) {
                adjustmentRepository.markApplied(id, runId, appliedAt);
            }
        });
    }

    /** 决策分级入口。就高不就低、规则命中、不可逆拦截全在 {@link DecisionRaiseService} 里。 */
    public RaisedDecision raise(Task task, ProjectProfile project, ExecResult result) {
        return decisionRaiseService.raise(new RaiseDecisionCommand(
                task.getId(),
                task.getProjectId(),
                project.defaultDecisionLevel(),
                result.decision().level(),
                result.decision().question(),
                result.decision().options(),
                result.decision().recommendation(),
                result.decision().reversible(),
                result.decision().blastRadius(),
                result.changedFiles()));
    }

    /** 挂起等人批准。任务已经是别的状态（比如被人终止了）时什么都不做。 */
    public void blockForHuman(TaskId taskId, DecisionId decisionId) {
        Task fresh = taskRepository.findById(taskId).orElse(null);
        if (fresh == null || fresh.getStatus() != TaskStatus.RUNNING) {
            return;
        }
        fresh.block(decisionId);
        taskRepository.save(fresh);
    }

    /**
     * 在同一事务里收敛执行记录与任务状态。
     *
     * <p>成功结果先锁任务行，再检查是否还有待下发调整。两步使用与「新增调整」相同的锁顺序：
     * 若调整先提交，这里会延迟完成；若完成先提交，后到的调整会看到 DONE 并被拒绝。
     * 这样既不会丢调整，也不会让任务在调整尚未处理时提前收场。
     */
    public AttemptFinish finishAttempt(TaskRun run, ExecResult result) {
        if (run == null || result == null) {
            throw new IllegalArgumentException("执行记录与执行结果不能为空");
        }
        return transactionRunner.inTransaction(() -> {
            Task fresh = taskRepository.findByIdForUpdate(run.getTaskId()).orElse(null);
            boolean latest = taskRunRepository.findLatestByTask(run.getTaskId())
                    .map(latestRun -> latestRun.getId().equals(run.getId()))
                    .orElse(false);
            boolean stillRunning = latest && fresh != null && fresh.getStatus() == TaskStatus.RUNNING;
            if (stillRunning && result.status() == RunStatus.SUCCESS
                    && adjustmentRepository.hasPending(run.getTaskId())) {
                return new AttemptFinish(run.getStatus(), fresh.getStatus(), true);
            }

            run.finish(result);
            taskRunRepository.save(run);
            if (!stillRunning) {
                return new AttemptFinish(run.getStatus(),
                        fresh == null ? null : fresh.getStatus(), false);
            }
            if (run.getStatus() == RunStatus.SUCCESS) {
                fresh.complete();
                taskRepository.save(fresh);
                markBugFixed(fresh);
                transactionRunner.afterCompletion(
                        () -> testCaseExecutionScheduler.scheduleAfterTaskCompleted(run.getTaskId()),
                        () -> {
                        });
                transactionRunner.afterCompletion(
                        () -> reviewSnapshotScheduler.scheduleAfterTaskCompleted(
                                ReviewTaskId.of(run.getTaskId().value()),
                                ReviewTaskRunId.of(run.getId().value())),
                        () -> {
                        });
            } else if (run.getStatus() != RunStatus.INTERRUPTED) {
                fresh.fail();
                taskRepository.save(fresh);
            }
            return new AttemptFinish(run.getStatus(), fresh.getStatus(), false);
        });
    }

    /** 修复任务成功只把缺陷推进到待验证，是否真正关闭仍由人决定。 */
    private void markBugFixed(Task task) {
        if (task.getSource() != TaskSource.BUG_FIX) {
            return;
        }
        task.getBugId()
                .flatMap(bugRepository::findById)
                .filter(bug -> bug.getStatus() == BugStatus.FIXING)
                .ifPresent(bug -> {
                    bug.markFixed();
                    bugRepository.save(bug);
                });
    }

    /** 任务是否仍处在「执行中」—— 只有它才允许被阻塞或收场。 */
    public boolean stillRunning(TaskId taskId) {
        return taskRepository.findById(taskId)
                .map(task -> task.getStatus() == TaskStatus.RUNNING)
                .orElse(false);
    }

    /**
     * 把执行器发现的缺陷落成独立聚合。
     *
     * <p>这一步不参与任务状态判定：发现缺陷不等于当前任务失败，也不代表 AI 已经修复它。
     * 两件事分开记账，后续才能给人留下「先派修复任务还是先继续原任务」的选择。
     */
    public void recordReportedBugs(Task task, ExecResult result) {
        for (ReportedBug reported : result.bugs()) {
            Bug bug = Bug.discoveredByAi(
                    task.getProjectId(),
                    task.getRequirementId().orElse(null),
                    task.getId(),
                    reported.title(),
                    BugSeverity.parse(reported.severity()),
                    reported.reproduction(),
                    reported.filePath(),
                    reported.detail());
            bugRepository.save(bug);
        }
    }

    /**
     * 回填会话 ID。同一会话重复回报时不写库，避免一次执行刷出十几条空 UPDATE。
     *
     * <p>任务已不是 RUNNING 时只把收到的值返回给调用方（内存里继续用它续跑），不写库：
     * 一条被人终止的任务不该因为执行器又报了一次会话就被改回去。
     */
    public String adoptThreadId(TaskId taskId, String reported, String current) {
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

    /**
     * 决策事件的事件流载荷。平台事件也是 JSON —— {@code run_event.payload} 是 JSON 列，
     * 写不进非 JSON 文本。这里只拼 ID 与枚举名，不含用户文本，因此不需要转义。
     */
    public static String decisionPayload(Decision decision) {
        return "{\"decisionId\":" + decision.getId()
                + ",\"status\":\"" + decision.getStatus()
                + "\",\"effectiveLevel\":\"" + decision.getEffectiveLevel()
                + "\"}";
    }
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    /** 一批待下发调整及已经拼好的执行指令。 */
    public record AdjustmentInstruction(List<TaskAdjustmentId> ids, String prompt) {

        public AdjustmentInstruction {
            ids = List.copyOf(ids);
        }
    }

    /** 一次执行记录收场后的状态；成功但有待调整时会延迟收场。 */
    public record AttemptFinish(RunStatus runStatus,
                                TaskStatus taskStatus,
                                boolean deferredForAdjustment) {
    }
}
