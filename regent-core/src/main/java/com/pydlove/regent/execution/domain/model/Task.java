package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 任务 —— 执行上下文的聚合根，本项目里唯一带完整状态机的对象。
 *
 * <pre>
 * PENDING ──queue──▶ QUEUED ──dispatch──▶ RUNNING ──complete──▶ DONE（终态）
 *    │                                      │
 *    └────────────dispatch────────────────┘
 *                                           ├──block──▶ BLOCKED ──resume──▶ RUNNING
 *                                           │               │
 *                                           │               └──cancel──▶ CANCELLED
 *                                           ├──fail──▶ FAILED ──retry──▶ PENDING
 *                                           └──cancel──▶ CANCELLED ──retry──▶ PENDING
 * </pre>
 *
 * <p><b>状态只能通过这些领域方法迁移</b>：没有 setStatus，没有公开 setter，
 * 每个方法开头用 {@code requireState} 守住前置状态。数据库里的 status 列只是本对象状态的
 * 投影，仓储负责把两者对齐 —— 反过来说，「直接 UPDATE status」是绕过状态机的后门，
 * 一旦出现，状态机就不再是唯一真相。
 *
 * <p>跨聚合的动作（例如「决策批准后把任务从 BLOCKED 拉回 RUNNING」）由应用服务显式编排：
 * decision 上下文先批准，再调这里的 {@link #resume()}。聚合之间只传递 ID，不持有对象引用。
 */
public class Task {

    /** 尚未持久化时为 null，由仓储 insert 后通过 {@link #assignIdentity} 回填。 */
    private TaskId id;
    private final ProjectId projectId;
    private final RequirementId requirementId;
    private final BugId bugId;
    private final TaskSource source;
    private final TaskId continuedFromTaskId;
    private String title;
    private String prompt;
    private int priority;

    private TaskStatus status;
    private String codexThreadId;
    private String branchName;
    private BranchApprovalState branchApprovalState;
    private DecisionId blockingDecisionId;
    private int attemptCount;
    private Instant startedAt;
    private Instant finishedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Task(ProjectId projectId,
                 RequirementId requirementId,
                 BugId bugId,
                 TaskSource source,
                 String title,
                 String prompt,
                 int priority,
                 Instant createdAt) {
        this(projectId, requirementId, bugId, source, null, null, null,
                title, prompt, priority, createdAt);
    }

    private Task(ProjectId projectId,
                 RequirementId requirementId,
                 BugId bugId,
                 TaskSource source,
                 TaskId continuedFromTaskId,
                 String codexThreadId,
                 String branchName,
                 String title,
                 String prompt,
                 int priority,
                 Instant createdAt) {
        this.projectId = requireNotNull(projectId, "所属项目");
        this.requirementId = requirementId;
        this.bugId = bugId;
        this.source = requireNotNull(source, "任务来源");
        this.continuedFromTaskId = continuedFromTaskId;
        this.title = requireTitle(title);
        this.prompt = requirePrompt(prompt);
        this.priority = priority;
        this.status = TaskStatus.PENDING;
        this.codexThreadId = continuedFromTaskId == null
                ? codexThreadId
                : requireText(codexThreadId, "来源任务的执行线程");
        this.branchName = blankToNull(branchName);
        this.branchApprovalState = BranchApprovalState.NONE;
        this.attemptCount = 0;
        this.createdAt = requireNotNull(createdAt, "创建时间");
        this.updatedAt = createdAt;
    }

    /** 由需求拆解而来的任务：必须携带需求引用。 */
    public static Task fromRequirement(ProjectId projectId, RequirementId requirementId,
                                       String title, String prompt, int priority) {
        return new Task(projectId, requireNotNull(requirementId, "需求"), null,
                TaskSource.REQUIREMENT, title, prompt, priority, now());
    }

    /** 由缺陷单派生的修复任务：必须携带缺陷引用，需求可以为空。 */
    public static Task fromBug(ProjectId projectId, BugId bugId,
                               String title, String prompt, int priority) {
        return new Task(projectId, null, requireNotNull(bugId, "缺陷单"),
                TaskSource.BUG_FIX, title, prompt, priority, now());
    }

    /** 人工录入的任务：没有上游引用。 */
    public static Task manual(ProjectId projectId, String title, String prompt, int priority) {
        return new Task(projectId, null, null, TaskSource.MANUAL, title, prompt, priority, now());
    }

    /** 基于已完成过会话的任务继续开发：来源关系、线程和分支在创建时固定下来。 */
    public static Task continuation(ProjectId projectId,
                                    TaskId sourceTaskId,
                                    String codexThreadId,
                                    String branchName,
                                    String title,
                                    String prompt,
                                    int priority) {
        return new Task(projectId, null, null, TaskSource.MANUAL,
                requireNotNull(sourceTaskId, "来源任务"),
                codexThreadId,
                branchName,
                title, prompt, priority, now());
    }

    /**
     * 从存储重建。只允许基础设施层调用 —— 领域层之外的生产代码不该走这条路，
     * 因为它跳过了所有状态守卫。
     */
    public static Task restore(TaskSnapshot snapshot) {
        Task task = new Task(
                snapshot.projectId(), snapshot.requirementId(), snapshot.bugId(), snapshot.source(),
                snapshot.continuedFromTaskId(), snapshot.codexThreadId(), snapshot.branchName(),
                snapshot.title(), snapshot.prompt(), snapshot.priority(), snapshot.createdAt());
        task.id = requireNotNull(snapshot.id(), "任务 ID");
        task.status = requireNotNull(snapshot.status(), "任务状态");
        task.branchApprovalState = snapshot.branchApprovalState() == null
                ? BranchApprovalState.NONE : snapshot.branchApprovalState();
        task.blockingDecisionId = snapshot.blockingDecisionId();
        task.attemptCount = snapshot.attemptCount();
        task.startedAt = snapshot.startedAt();
        task.finishedAt = snapshot.finishedAt();
        task.updatedAt = requireNotNull(snapshot.updatedAt(), "更新时间");
        return task;
    }

    /** 持久化后回填数据库生成的主键。重复分配直接报错，避免把两行数据悄悄并成一行。 */
    public void assignIdentity(TaskId assigned) {
        if (this.id != null) {
            throw new DomainException("任务已有 ID，不能重复分配：" + this.id);
        }
        this.id = requireNotNull(assigned, "任务 ID");
    }

    /**
     * 排队等待执行位。任务还没有真正执行，因此不增加尝试次数，也不写开始时间。
     */
    public void queue() {
        requireState(TaskStatus.PENDING, "只有待执行任务可以进入排队");
        this.status = TaskStatus.QUEUED;
        touch();
    }

    /**
     * 派发执行。首次派发和排队后的起跑都走这里。
     *
     * <p>分支名由调用方（执行编排）传入而不是这里拼 {@code codex/task-N}：命名策略属于
     * 分支管理端口。普通目录、空仓库或多仓库父目录没有分支名，传 null 仍可正常派发。
     */
    public void dispatch(String branchName) {
        requireStateIn("只有待执行或排队中的任务可以派发", TaskStatus.PENDING, TaskStatus.QUEUED);
        this.branchName = blankToNull(branchName);
        this.branchApprovalState = BranchApprovalState.NONE;
        this.status = TaskStatus.RUNNING;
        this.attemptCount += 1;
        this.startedAt = now();
        this.finishedAt = null;
        touch();
    }

    /** 执行中阻塞，等待人工决策。 */
    public void block(DecisionId decisionId) {
        requireState(TaskStatus.RUNNING, "只有执行中的任务可以阻塞");
        this.blockingDecisionId = requireNotNull(decisionId, "阻塞决策");
        this.status = TaskStatus.BLOCKED;
        touch();
    }

    /**
     * 首次执行需要创建分支，先挂起等人工批准。
     *
     * <p>只允许从尚未执行的 PENDING/QUEUED 进入：分支创建审批必须发生在任何 Git 副作用之前，
     * 已跑过的任务不会走这条路径。调用方负责在决策单落库后再调用本方法。
     */
    public void requestBranchApproval(DecisionId decisionId) {
        requireStateIn("只有待执行或排队中的任务可以申请创建分支",
                TaskStatus.PENDING, TaskStatus.QUEUED);
        if (branchApprovalState != BranchApprovalState.NONE) {
            throw new DomainException("任务已有分支审批状态，不能重复申请：" + branchApprovalState);
        }
        this.blockingDecisionId = requireNotNull(decisionId, "分支审批决策");
        this.branchApprovalState = BranchApprovalState.PENDING;
        this.status = TaskStatus.BLOCKED;
        touch();
    }

    /** 分支创建获得人工批准。任务回到排队中，下一次派发才允许真正创建分支。 */
    public void approveBranchCreation() {
        requireState(TaskStatus.BLOCKED, "只有阻塞中的任务可以批准创建分支");
        if (branchApprovalState != BranchApprovalState.PENDING) {
            throw new DomainException("任务当前没有待批准的分支创建申请：" + branchApprovalState);
        }
        this.blockingDecisionId = null;
        this.branchApprovalState = BranchApprovalState.APPROVED;
        this.status = TaskStatus.QUEUED;
        touch();
    }

    /** 分支创建被驳回。与普通任务终止一样落 CANCELLED，但清掉审批状态，重试时重新申请。 */
    public void rejectBranchCreation() {
        requireState(TaskStatus.BLOCKED, "只有阻塞中的任务可以驳回创建分支");
        if (branchApprovalState != BranchApprovalState.PENDING) {
            throw new DomainException("任务当前没有待批准的分支创建申请：" + branchApprovalState);
        }
        this.blockingDecisionId = null;
        this.branchApprovalState = BranchApprovalState.NONE;
        this.status = TaskStatus.CANCELLED;
        if (this.finishedAt == null) {
            this.finishedAt = now();
        }
        touch();
    }

    /** 决策归位后续跑（批准走这里；驳回走 {@link #cancel()}）。 */
    public void resume() {
        requireState(TaskStatus.BLOCKED, "只有阻塞中的任务可以续跑");
        this.blockingDecisionId = null;
        this.status = TaskStatus.RUNNING;
        touch();
    }

    /**
     * 开启一次续跑尝试 —— 决策批准之后，接着上一轮的会话继续执行。
     *
     * <p>与 {@link #dispatch(String)} 的区别只有入口状态与凭据：dispatch 是「第一次派发」；
     * 本方法是「带着人拍的板接着跑」，必须有可续跑的会话。有分支时仍留在原来那条分支，
     * 没有分支时继续在项目目录当前状态执行。
     *
     * <p>它必须在执行器真的被拉起来之前由编排层调用：尝试次数是「执行器跑了几次」的计数，
     * 提前加一，历史里就会出现一条没有对应执行记录的尝试。
     */
    public void beginContinuation() {
        requireState(TaskStatus.RUNNING, "只有执行中的任务可以开启续跑");
        if (codexThreadId == null) {
            throw new DomainException("任务没有可续跑的会话（codex_thread_id 为空），只能重新派发：" + id);
        }
        this.attemptCount += 1;
        this.startedAt = now();
        this.finishedAt = null;
        touch();
    }

    /**
     * 平台重启后，把被中断的执行重新放回队列。
     *
     * <p>这不是一次新的执行尝试：尝试次数和执行记录都由紧接着的派发流程创建。
     * 这里保留 {@code codexThreadId}，下一次派发才能用 {@code codex exec resume}
     * 接着原会话继续；有分支则保留，避免把上一轮改动留在一条无人接管的分支上。
     */
    public void requeueAfterInterruption() {
        requireState(TaskStatus.RUNNING, "只有执行中的任务可以在中断后重新排队");
        if (blockingDecisionId != null) {
            throw new DomainException("任务仍在等待决策，不能按重启中断恢复：" + id);
        }
        this.status = TaskStatus.QUEUED;
        this.startedAt = null;
        this.finishedAt = null;
        touch();
    }

    public void complete() {
        requireState(TaskStatus.RUNNING, "只有执行中的任务可以完成");
        this.status = TaskStatus.DONE;
        this.finishedAt = now();
        touch();
    }

    public void fail() {
        requireState(TaskStatus.RUNNING, "只有执行中的任务可以判定失败");
        this.status = TaskStatus.FAILED;
        this.finishedAt = now();
        touch();
    }

    /**
     * 人工重试或重新执行：FAILED / CANCELLED 回到 PENDING，等下一轮派发。
     * 历史尝试次数保留，用于追溯反复失败或多次终止的任务。
     */
    public void retry() {
        requireStateIn("只有失败或已终止的任务可以重试", TaskStatus.FAILED, TaskStatus.CANCELLED);
        this.branchApprovalState = BranchApprovalState.NONE;
        this.status = TaskStatus.PENDING;
        this.startedAt = null;
        this.finishedAt = null;
        touch();
    }

    /**
     * 修改任务内容。只有已完成任务禁止编辑；终止、失败等历史状态仍允许修正记录，
     * 但修改内容不会伪装成一次新的执行尝试。
     */
    public void edit(String title, String prompt, int priority) {
        if (this.status == TaskStatus.DONE) {
            throw new DomainException("已完成的任务不能编辑");
        }
        this.title = requireTitle(title);
        this.prompt = requirePrompt(prompt);
        this.priority = priority;
        touch();
    }

    /**
     * 调整方向只对已经进入执行链路、但尚未收场的任务开放。
     *
     * <p>PENDING 任务仍走完整编辑；DONE/FAILED/CANCELLED 的任务没有正在进行的上下文，
     * 接受调整只会制造一条永远不会被执行的记录。
     */
    public void requireAcceptsAdjustment() {
        requireStateIn("只有已提交执行且尚未收场的任务可以增加调整方向",
                TaskStatus.QUEUED, TaskStatus.RUNNING, TaskStatus.BLOCKED);
    }

    /** 只有建立过 Codex 会话的任务才能作为后续任务的上游。 */
    public void requireContinuable() {
        requireStateIn("只有执行中、待批准或已收场的任务可以继续创建任务",
                TaskStatus.RUNNING, TaskStatus.BLOCKED,
                TaskStatus.DONE, TaskStatus.FAILED, TaskStatus.CANCELLED);
        if (codexThreadId == null) {
            throw new DomainException("任务尚未建立 Codex 会话，不能继续创建任务：" + id);
        }
    }

    /** 终止任务。除终态外都可终止，包括阻塞中（等价于驳回该决策）。 */
    public void cancel() {
        if (this.status.isTerminal()) {
            throw new DomainException("任务已处于终态，不能终止：" + this.status);
        }
        this.status = TaskStatus.CANCELLED;
        this.blockingDecisionId = null;
        this.branchApprovalState = BranchApprovalState.NONE;
        if (this.finishedAt == null) {
            this.finishedAt = now();
        }
        touch();
    }

    /**
     * 删除守卫。
     *
     * <p>尚未执行过的待执行、排队任务，以及已经人工终止的任务允许删除；删除本身由仓储做
     * 逻辑删除，聚合不需要知道 {@code deleted} 列。执行中、阻塞、失败或已完成的任务必须留在
     * 平台上供审计与追溯。
     */
    public void requireDeletable() {
        requireStateIn("只有待执行、排队中或已终止的任务可以删除",
                TaskStatus.PENDING, TaskStatus.QUEUED, TaskStatus.CANCELLED);
    }

    /** 执行器回报断点续跑的钥匙，取自 {@code codex exec --json} 的 thread_id。 */
    public void recordThreadId(String threadId) {
        if (this.status != TaskStatus.RUNNING) {
            throw new DomainException("只有执行中的任务可以记录执行线程：" + this.status);
        }
        this.codexThreadId = requireText(threadId, "执行线程 ID");
        touch();
    }

    /** AI 执行后决定为本任务创建隔离分支；只更新记录，不触碰分支本身。 */
    public void adoptBranch(String branchName) {
        if (this.status != TaskStatus.RUNNING) {
            throw new DomainException("只有执行中的任务可以记录实际分支：" + this.status);
        }
        this.branchName = requireText(branchName, "分支名");
        touch();
    }

    private void requireState(TaskStatus expected, String message) {
        if (this.status != expected) {
            throw new DomainException(message + "，当前状态：" + this.status);
        }
    }

    private void requireStateIn(String message, TaskStatus... expected) {
        for (TaskStatus status : expected) {
            if (this.status == status) {
                return;
            }
        }
        throw new DomainException(message + "，当前状态：" + this.status);
    }

    private void touch() {
        this.updatedAt = now();
    }

    /**
     * 统一取当前时间，精度截到毫秒。
     *
     * <p>不是「因为数据库是 DATETIME(3) 所以就截断」，而是反过来：平台的执行记录精度定为毫秒，
     * 数据库按这个精度建列。截断的作用是让内存里的对象与存进去的行时间完全相等 ——
     * 不截断的话，纳秒值会在写入时被四舍五入，同一个对象读回来就"变了"，
     * 这类差异在排查「为什么两次查询的结果不一样」时极难定位。
     */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String requireTitle(String title) {
        requireText(title, "任务标题");
        if (title.length() > 256) {
            throw new DomainException("任务标题超长（上限 256 字符）：" + title.length());
        }
        return title;
    }

    private static String requirePrompt(String prompt) {
        requireText(prompt, "执行指令");
        return prompt;
    }

    public TaskId getId() {
        return id;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public Optional<RequirementId> getRequirementId() {
        return Optional.ofNullable(requirementId);
    }

    public Optional<BugId> getBugId() {
        return Optional.ofNullable(bugId);
    }

    public TaskSource getSource() {
        return source;
    }

    public Optional<TaskId> getContinuedFromTaskId() {
        return Optional.ofNullable(continuedFromTaskId);
    }

    public boolean isContinuation() {
        return continuedFromTaskId != null;
    }

    public String getTitle() {
        return title;
    }

    public String getPrompt() {
        return prompt;
    }

    public int getPriority() {
        return priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Optional<String> getCodexThreadId() {
        return Optional.ofNullable(codexThreadId);
    }

    public Optional<String> getBranchName() {
        return Optional.ofNullable(branchName);
    }

    public BranchApprovalState getBranchApprovalState() {
        return branchApprovalState;
    }

    public boolean isBranchCreationApproved() {
        return branchApprovalState == BranchApprovalState.APPROVED;
    }

    public boolean isAwaitingBranchApproval() {
        return branchApprovalState == BranchApprovalState.PENDING;
    }

    public Optional<DecisionId> getBlockingDecisionId() {
        return Optional.ofNullable(blockingDecisionId);
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Optional<Instant> getStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> getFinishedAt() {
        return Optional.ofNullable(finishedAt);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
