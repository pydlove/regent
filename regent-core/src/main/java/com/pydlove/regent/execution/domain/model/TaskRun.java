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

    private final Instant startedAt;
    private Instant finishedAt;
    private final Instant createdAt;

    private TaskRun(TaskId taskId, int attemptNo, String branchName, Instant startedAt, Instant createdAt) {
        this.taskId = requireNotNull(taskId, "所属任务");
        if (attemptNo < 1) {
            throw new DomainException("尝试序号从 1 开始：" + attemptNo);
        }
        this.attemptNo = attemptNo;
        this.branchName = requireText(branchName, "分支名");
        this.status = RunStatus.RUNNING;
        this.tokens = TokenUsage.ZERO;
        this.changedFiles = List.of();
        this.schemaValid = true;
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
