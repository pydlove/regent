package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 执行记录 —— 任务第 N 次尝试留下的事实。
 *
 * <p>它回答的问题与 {@link Task} 不同：任务说的是「现在处于什么状态」，
 * 执行记录说的是「这一次到底跑了什么、花了多少、改了什么、结论是什么」。
 * 任务可以重试多次，而每一次都应当留下一行不可改写的记录 —— 这是复盘与算账的依据。
 *
 * <p>两条不变量由本对象守住：
 * <ol>
 *   <li><b>一行记录只收敛一次。</b>结论写两遍，历史里就会出现「同一次执行既成功又失败」；</li>
 *   <li><b>收敛前必须先落行。</b>事件流的外键指向本行，先跑后写会让运行中的日志无处挂靠，
 *       所以工厂方法一建出来就是 {@link RunStatus#RUNNING}。</li>
 * </ol>
 *
 * <p>自动决断触发的续跑<b>不会</b>新开一行记录：一次尝试就是一次尝试，中间问了几轮
 * 留在事件流与决策单里。只有人工批准之后的续跑才算新的一次尝试 —— 中间隔着人的时间。
 */
public class TaskRun {

    /** 平台自己拉起的本机 codex。执行者身份的默认值，也是绝大多数记录的取值。 */
    public static final String EXECUTOR_INTERNAL = "codex-cli";

    /** 外部执行者：codex 桌面版、别的 CLI、别的模型工具。它靠租约证明自己还活着。 */
    public static final String EXECUTOR_AGENT = "agent";

    /** 尚未持久化时为 null，由仓储 insert 后通过 {@link #assignIdentity} 回填。 */
    private TaskRunId id;
    private final TaskId taskId;
    private final int attemptNo;
    private final String branchName;

    private RunStatus status;
    private Integer exitCode;
    private TokenUsage tokens;
    private String summary;
    private String rawResult;
    private boolean schemaValid;
    private List<String> changedFiles;
    private String executor;
    private String agentName;
    private Instant leaseExpiresAt;

    private final Instant startedAt;
    private Instant finishedAt;
    private final Instant createdAt;

    private TaskRun(TaskId taskId, int attemptNo, String branchName, Instant startedAt, Instant createdAt) {
        this.taskId = requireNotNull(taskId, "所属任务");
        if (attemptNo < 1) {
            throw new DomainException("尝试序号从 1 开始：" + attemptNo);
        }
        this.attemptNo = attemptNo;
        this.branchName = blankToNull(branchName);
        this.status = RunStatus.RUNNING;
        this.tokens = TokenUsage.ZERO;
        this.changedFiles = List.of();
        this.schemaValid = true;
        this.executor = EXECUTOR_INTERNAL;
        this.startedAt = requireNotNull(startedAt, "开始时间");
        this.createdAt = requireNotNull(createdAt, "创建时间");
    }

    /** 开跑。调用方负责先把它落库再把 ID 交出去，事件流才有挂靠点。 */
    public static TaskRun start(TaskId taskId, int attemptNo, String branchName) {
        Instant now = now();
        return new TaskRun(taskId, attemptNo, branchName, now, now);
    }

    /**
     * 从存储重建。只允许基础设施层调用 —— 它跳过所有状态守卫。
     */
    public static TaskRun restore(TaskRunSnapshot snapshot) {
        TaskRun run = new TaskRun(snapshot.taskId(), snapshot.attemptNo(), snapshot.branchName(),
                snapshot.startedAt(), snapshot.createdAt());
        run.id = requireNotNull(snapshot.id(), "执行记录 ID");
        run.status = requireNotNull(snapshot.status(), "执行状态");
        run.exitCode = snapshot.exitCode();
        run.tokens = snapshot.tokens() == null ? TokenUsage.ZERO : snapshot.tokens();
        run.summary = snapshot.summary();
        run.rawResult = snapshot.rawResult();
        run.schemaValid = snapshot.schemaValid();
        run.changedFiles = snapshot.changedFiles() == null ? List.of() : List.copyOf(snapshot.changedFiles());
        run.executor = snapshot.executor() == null ? EXECUTOR_INTERNAL : snapshot.executor();
        run.agentName = snapshot.agentName();
        run.leaseExpiresAt = snapshot.leaseExpiresAt();
        run.finishedAt = snapshot.finishedAt();
        return run;
    }

    /**
     * 写入终局结论。
     *
     * <p>schema 不合法的结果在这里就被判为失败：出参不按约定来，平台读不到摘要与决策，
     * 让它继续往下走只会把「读不懂」伪装成「没消息」。
     */
    public void finish(ExecResult result) {
        requireNotNull(result, "执行结果");
        if (this.status.isFinished()) {
            throw new DomainException("执行记录已收敛（" + this.status + "），不能重复写入结论");
        }
        this.status = result.schemaValid() ? result.status() : RunStatus.FAILED;
        this.exitCode = result.exitCode();
        this.tokens = result.tokens();
        this.summary = blankToNull(result.summary());
        this.rawResult = blankToNull(result.rawResult());
        this.schemaValid = result.schemaValid();
        this.changedFiles = result.changedFiles();
        this.finishedAt = now();
        this.leaseExpiresAt = null;
    }

    /**
     * 平台进程重启导致本次执行失去执行者。
     *
     * <p>只适用于平台自己拉起的内部执行：外部执行者有租约，失联由租约回收处理，
     * 不能因为平台重启就把别人还在跑的记录判成中断。
     */
    public void interrupt(String reason) {
        requireRunning("只有执行中的记录可以被中断");
        if (!EXECUTOR_INTERNAL.equals(executor)) {
            throw new DomainException("外部执行者的记录不能按平台重启中断：" + id);
        }
        this.status = RunStatus.INTERRUPTED;
        this.summary = requireText(reason, "中断原因");
        this.finishedAt = now();
        this.leaseExpiresAt = null;
    }

    /**
     * 外部执行者认领这次尝试。
     *
     * <p>租约是这条路唯一的活性证明：外部执行者跑在别人的进程里，平台既看不见它、
     * 也杀不掉它。它一旦不回话，租约到期就由回收任务把这次尝试判为失联 ——
     * 没有租约的话，一个人中途关掉编辑器，任务会永远停在「执行中」。
     */
    public void claimBy(String agentName, Instant leaseExpiresAt) {
        requireRunning("执行记录已收场，不能再被认领");
        this.executor = EXECUTOR_AGENT;
        this.agentName = requireText(agentName, "执行者");
        this.leaseExpiresAt = requireNotNull(leaseExpiresAt, "租约到期时间");
    }

    /** 续租。执行者每次心跳续一次；不是自己的租约不能续。 */
    public void renewLease(String agentName, Instant leaseExpiresAt) {
        requireRunning("执行记录已收场，不能再续租");
        if (!isLeaseHolder(agentName)) {
            throw new DomainException("这条执行记录不属于该执行者，不能续租：" + agentName);
        }
        this.leaseExpiresAt = requireNotNull(leaseExpiresAt, "租约到期时间");
    }

    /** 这条记录的租约是不是这个执行者持有的。 */
    public boolean isLeaseHolder(String agentName) {
        return EXECUTOR_AGENT.equals(executor) && this.agentName != null && this.agentName.equals(agentName);
    }

    /** 租约是否已到期。没有租约（内部执行者、或已收场）永远返回 false。 */
    public boolean isLeaseExpiredAt(Instant now) {
        return leaseExpiresAt != null && !leaseExpiresAt.isAfter(now);
    }

    /** 有没有外部执行者正拿着它。 */
    public boolean hasLease() {
        return EXECUTOR_AGENT.equals(executor) && leaseExpiresAt != null;
    }

    private void requireRunning(String message) {
        if (status != RunStatus.RUNNING) {
            throw new DomainException(message + "，当前状态：" + status);
        }
    }

    /** 持久化后回填数据库生成的主键。重复分配直接报错。 */
    public void assignIdentity(TaskRunId assigned) {
        if (this.id != null) {
            throw new DomainException("执行记录已有 ID，不能重复分配：" + this.id);
        }
        this.id = requireNotNull(assigned, "执行记录 ID");
    }

    public TaskRunId getId() {
        return id;
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public String getBranchName() {
        return branchName;
    }

    public RunStatus getStatus() {
        return status;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public TokenUsage getTokens() {
        return tokens;
    }

    public String getSummary() {
        return summary;
    }

    public String getRawResult() {
        return rawResult;
    }

    public boolean isSchemaValid() {
        return schemaValid;
    }

    public List<String> getChangedFiles() {
        return changedFiles;
    }

    /** 这条记录是谁跑的：{@link #EXECUTOR_INTERNAL} 或 {@link #EXECUTOR_AGENT}。 */
    public String getExecutor() {
        return executor;
    }

    /** 外部执行者的自称，仅用于界面显示与追责；内部执行者为 null。 */
    public String getAgentName() {
        return agentName;
    }

    /** 租约到期时间；没有外部执行者持有（或已收场）时为 null。 */
    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
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
}
