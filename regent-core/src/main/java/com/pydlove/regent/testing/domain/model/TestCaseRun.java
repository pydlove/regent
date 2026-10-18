package com.pydlove.regent.testing.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 一条测试执行记录。
 *
 * <p>命令与脚本在入队时快照，后续编辑用例不会改写历史。输出只保留配置上限内的内容，
 * 避免一个刷屏的测试把审计表撑爆。
 */
public class TestCaseRun {

    public static final int MAX_OUTPUT_LENGTH = 512 * 1024;
    private static final int MAX_SUMMARY_LENGTH = 1_024;

    private TestCaseRunId id;
    private final ProjectId projectId;
    private final TaskId taskId;
    private final TestCaseRunTrigger trigger;
    private final TestCaseId testCaseId;
    private final String testCaseName;
    private final TestCaseType type;
    private final String command;
    private final String scriptFilename;
    private final String scriptContent;
    private final String scriptHash;

    private TestCaseRunStatus status;
    private Integer exitCode;
    private String summary;
    private String output;
    private final Instant queuedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private final Instant createdAt;

    private TestCaseRun(ProjectId projectId,
                        TaskId taskId,
                        TestCaseRunTrigger trigger,
                        TestCaseId testCaseId,
                        String testCaseName,
                        TestCaseType type,
                        String command,
                        String scriptFilename,
                        String scriptContent,
                        String scriptHash,
                        TestCaseRunStatus status,
                        Integer exitCode,
                        String summary,
                        String output,
                        Instant queuedAt,
                        Instant startedAt,
                        Instant finishedAt,
                        Instant createdAt) {
        this.projectId = requireNotNull(projectId, "项目 ID");
        this.trigger = requireNotNull(trigger, "测试触发类型");
        if (trigger == TestCaseRunTrigger.TASK_COMPLETION && taskId == null) {
            throw new DomainException("任务完成触发的测试必须带任务 ID");
        }
        if (trigger == TestCaseRunTrigger.MANUAL && taskId != null) {
            throw new DomainException("手动执行的测试不能带任务 ID");
        }
        this.taskId = taskId;
        this.testCaseId = requireNotNull(testCaseId, "测试用例 ID");
        this.testCaseName = requireText(testCaseName, "测试用例名称");
        this.type = requireNotNull(type, "测试类型");
        this.command = blankToNull(command);
        this.scriptFilename = blankToNull(scriptFilename);
        this.scriptContent = blankToNull(scriptContent);
        this.scriptHash = blankToNull(scriptHash);
        this.status = requireNotNull(status, "执行状态");
        this.exitCode = exitCode;
        this.summary = summary(summary);
        this.output = truncate(output, MAX_OUTPUT_LENGTH);
        this.queuedAt = requireNotNull(queuedAt, "入队时间");
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.createdAt = requireNotNull(createdAt, "创建时间");
    }

    public static TestCaseRun queueForTask(TaskId taskId, ProjectId projectId, TestCaseBinding binding) {
        Instant now = now();
        return new TestCaseRun(projectId, taskId, TestCaseRunTrigger.TASK_COMPLETION,
                binding.testCaseId(), binding.name(), binding.type(),
                binding.command(), binding.scriptFilename(), binding.scriptContent(), binding.scriptHash(),
                TestCaseRunStatus.QUEUED, null, null, null, now, null, null, now);
    }

    public static TestCaseRun queueManual(ProjectId projectId, TestCaseBinding binding) {
        Instant now = now();
        return new TestCaseRun(projectId, null, TestCaseRunTrigger.MANUAL,
                binding.testCaseId(), binding.name(), binding.type(),
                binding.command(), binding.scriptFilename(), binding.scriptContent(), binding.scriptHash(),
                TestCaseRunStatus.QUEUED, null, null, null, now, null, null, now);
    }

    public static TestCaseRun restore(TestCaseRunId id,
                                      ProjectId projectId,
                                      TaskId taskId,
                                      TestCaseRunTrigger trigger,
                                      TestCaseId testCaseId,
                                      String testCaseName,
                                      TestCaseType type,
                                      String command,
                                      String scriptFilename,
                                      String scriptContent,
                                      String scriptHash,
                                      TestCaseRunStatus status,
                                      Integer exitCode,
                                      String summary,
                                      String output,
                                      Instant queuedAt,
                                      Instant startedAt,
                                      Instant finishedAt,
                                      Instant createdAt) {
        TestCaseRun run = new TestCaseRun(projectId, taskId, trigger, testCaseId,
                testCaseName, type, command,
                scriptFilename, scriptContent, scriptHash, status, exitCode, summary, output,
                queuedAt, startedAt, finishedAt, createdAt);
        run.id = requireNotNull(id, "测试执行记录 ID");
        return run;
    }

    public void begin() {
        requireStatus(TestCaseRunStatus.QUEUED, "只有排队中的测试可以开始");
        this.status = TestCaseRunStatus.RUNNING;
        this.startedAt = now();
    }

    public void finish(TestCaseExecutionResult result) {
        requireStatus(TestCaseRunStatus.RUNNING, "只有执行中的测试可以写入结论");
        this.status = result.status();
        this.exitCode = result.exitCode();
        this.summary = summary(result.summary());
        this.output = truncate(result.output(), MAX_OUTPUT_LENGTH);
        this.finishedAt = now();
    }

    public void skip(String reason) {
        requireStatus(TestCaseRunStatus.QUEUED, "只有排队中的测试可以跳过");
        this.status = TestCaseRunStatus.SKIPPED;
        this.summary = summary(reason);
        this.finishedAt = now();
    }

    public void interrupt(String reason) {
        if (status != TestCaseRunStatus.QUEUED && status != TestCaseRunStatus.RUNNING) {
            throw new DomainException("只有未收场的测试可以中断，当前状态：" + status);
        }
        this.status = TestCaseRunStatus.INTERRUPTED;
        this.summary = summary(reason);
        this.finishedAt = now();
    }

    public void assignIdentity(TestCaseRunId assigned) {
        if (id != null) {
            throw new DomainException("测试执行记录已有 ID，不能重复分配：" + id);
        }
        this.id = requireNotNull(assigned, "测试执行记录 ID");
    }

    private void requireStatus(TestCaseRunStatus expected, String message) {
        if (status != expected) {
            throw new DomainException(message + "，当前状态：" + status);
        }
    }

    private static String summary(String value) {
        return truncate(blankToNull(value), MAX_SUMMARY_LENGTH);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "\n…（输出已截断）";
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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

    public TestCaseRunId getId() {
        return id;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public Optional<TaskId> getTaskId() {
        return Optional.ofNullable(taskId);
    }

    public TestCaseRunTrigger getTrigger() {
        return trigger;
    }

    public TestCaseId getTestCaseId() {
        return testCaseId;
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    public TestCaseType getType() {
        return type;
    }

    public String getCommand() {
        return command;
    }

    public String getScriptFilename() {
        return scriptFilename;
    }

    public String getScriptContent() {
        return scriptContent;
    }

    public String getScriptHash() {
        return scriptHash;
    }

    public TestCaseRunStatus getStatus() {
        return status;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getSummary() {
        return summary;
    }

    public String getOutput() {
        return output;
    }

    public Instant getQueuedAt() {
        return queuedAt;
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
}
