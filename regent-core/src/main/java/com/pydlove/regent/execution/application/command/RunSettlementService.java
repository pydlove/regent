package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.model.BugStatus;
import com.pydlove.regent.bug.domain.repository.BugRepository;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.ReportedBug;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.model.TaskSource;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.governance.application.command.DecisionRaiseService;
import com.pydlove.regent.governance.application.command.RaiseDecisionCommand;
import com.pydlove.regent.governance.application.command.RaisedDecision;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.governance.domain.model.ResolvedBy;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

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
 * <p>它不认识进程、不认识 JSON、不认识 HTTP，因此放在 application 层；
 * 执行记录本身的收敛（{@code TaskRun.finish}）留给调用方，因为「什么时候算收场」
 * 两条路径并不一样：内部路径一次尝试一行到底，外部路径上报之后可能还要等心跳。
 */
public class RunSettlementService {

    private static final String RETRY_INSTRUCTION =
            "平台重试：上一轮执行没有跑完（原因见上一轮最后的说明）。请接着上一轮的进展继续完成任务，"
                    + "不要重复已经做好的部分，并按约定再次上报。";

    private final TaskRepository taskRepository;
    private final DecisionRepository decisionRepository;
    private final DecisionRaiseService decisionRaiseService;
    private final BugRepository bugRepository;

    public RunSettlementService(TaskRepository taskRepository,
                                DecisionRepository decisionRepository,
                                DecisionRaiseService decisionRaiseService,
                                BugRepository bugRepository) {
        this.taskRepository = taskRepository;
        this.decisionRepository = decisionRepository;
        this.decisionRaiseService = decisionRaiseService;
        this.bugRepository = bugRepository;
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
        if (previous == null || previous.getStatus() != RunStatus.BLOCKED) {
            return RETRY_INSTRUCTION;
        }
        Decision decision = decisionRepository.findLatestResolvedByTask(task.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "任务停在待续跑上，但找不到已决议的决策：" + task.getId()));
        return continuationPrompt(decision);
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
     * 按执行结果推进任务状态。
     *
     * <p>每次迁移前重新读一次任务：一次执行可能持续几十分钟，期间人可能在界面上点了「终止」，
     * 手里那份 {@code Task} 是开局时读的，直接拿它 complete/fail 会把人工终止悄悄覆盖回去。
     */
    public void finishTask(TaskId taskId, RunStatus outcome) {
        Task fresh = taskRepository.findById(taskId).orElse(null);
        if (fresh == null || fresh.getStatus() != TaskStatus.RUNNING) {
            return;
        }
        if (outcome == RunStatus.SUCCESS) {
            fresh.complete();
            taskRepository.save(fresh);
            markBugFixed(fresh);
            return;
        } else {
            fresh.fail();
        }
        taskRepository.save(fresh);
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
}
