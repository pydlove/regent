package com.pydlove.regent.testing.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.project.application.query.EnvironmentContext;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseBinding;
import com.pydlove.regent.testing.domain.model.TestCaseExecutionRequest;
import com.pydlove.regent.testing.domain.model.TestCaseExecutionResult;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseRun;
import com.pydlove.regent.testing.domain.model.TestCaseRunId;
import com.pydlove.regent.testing.domain.model.TestCaseRunStatus;
import com.pydlove.regent.testing.domain.port.TestCaseExecutionScheduler;
import com.pydlove.regent.testing.domain.port.TestCaseExecutor;
import com.pydlove.regent.testing.domain.repository.TaskTestCaseRepository;
import com.pydlove.regent.testing.domain.repository.TestCaseRepository;
import com.pydlove.regent.testing.domain.repository.TestCaseRunRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 测试执行编排：任务完成后的自动验证，以及菜单触发的单条/批量手动执行。
 *
 * <p>三件事刻意分开：
 * <ol>
 *   <li>测试失败不改任务状态 —— codex 的产物已经完成，测试是验收证据，不是回滚命令；</li>
 *   <li>测试工作线程独立于 codex 执行池 —— 测试再慢也不该占着下一条任务的执行位；</li>
 *   <li>记录先入队再执行 —— 平台重启时至少能留下「哪些测试没跑完」的痕迹。</li>
 * </ol>
 */
public class TestCaseExecutionService implements TestCaseExecutionScheduler {

    public static final String RESTART_INTERRUPT_REASON = "平台重启，测试进程已退出";
    public static final int MAX_MANUAL_BATCH = 50;

    private final TaskRepository taskRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final EnvironmentEntryRepository environmentEntryRepository;
    private final TaskTestCaseRepository taskTestCaseRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseRunRepository runRepository;
    private final TestCaseExecutor testCaseExecutor;
    private final ExecutorService workers;
    private final TransactionRunner transactionRunner;
    private final Set<Long> scheduledTasks = ConcurrentHashMap.newKeySet();

    public TestCaseExecutionService(TaskRepository taskRepository,
                                    ProjectProfileRepository projectProfileRepository,
                                    EnvironmentEntryRepository environmentEntryRepository,
                                    TaskTestCaseRepository taskTestCaseRepository,
                                    TestCaseRepository testCaseRepository,
                                    TestCaseRunRepository runRepository,
                                    TestCaseExecutor testCaseExecutor,
                                    ExecutorService workers,
                                    TransactionRunner transactionRunner) {
        this.taskRepository = taskRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.environmentEntryRepository = environmentEntryRepository;
        this.taskTestCaseRepository = taskTestCaseRepository;
        this.testCaseRepository = testCaseRepository;
        this.runRepository = runRepository;
        this.testCaseExecutor = testCaseExecutor;
        this.workers = workers;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public void scheduleAfterTaskCompleted(TaskId taskId) {
        if (!scheduledTasks.add(taskId.value())) {
            return;
        }
        List<TestCaseRunId> queued;
        try {
            queued = transactionRunner.inTransaction(() -> queueRuns(taskId));
        } catch (RuntimeException e) {
            scheduledTasks.remove(taskId.value());
            return;
        }
        if (queued.isEmpty()) {
            scheduledTasks.remove(taskId.value());
            return;
        }
        transactionRunner.afterCompletion(
                () -> submit(taskId, queued),
                () -> {
                    interruptRuns(queued, "任务结算事务回滚，测试未执行");
                    scheduledTasks.remove(taskId.value());
                });
    }

    private void submit(TaskId taskId, List<TestCaseRunId> queued) {
        try {
            workers.execute(() -> {
                try {
                    executeQueued(queued);
                } finally {
                    scheduledTasks.remove(taskId.value());
                }
            });
        } catch (RuntimeException e) {
            interruptRuns(queued, "测试工作线程未能启动");
            scheduledTasks.remove(taskId.value());
        }
    }

    /**
     * 从测试用例菜单立即执行。与任务完成的区别只有触发类型和任务号；
     * 命令快照、项目环境、线程池和执行记录都走同一条链路。
     */
    public TestCaseRun runNow(TestCaseId testCaseId) {
        TestCaseManualRunResult result = runMany(List.of(testCaseId));
        if (!result.failures().isEmpty()) {
            throw new DomainException(result.failures().get(0).message());
        }
        return result.runs().get(0);
    }

    public TestCaseManualRunResult runMany(List<TestCaseId> testCaseIds) {
        List<TestCaseId> unique = normalizeManualIds(testCaseIds);
        TestCaseManualRunResult result = transactionRunner.inTransaction(
                () -> createManualRuns(unique));
        List<TestCaseRunId> queued = result.runs().stream()
                .map(TestCaseRun::getId)
                .toList();
        if (!queued.isEmpty()) {
            transactionRunner.afterCompletion(
                    () -> submitManual(queued),
                    () -> interruptRuns(queued, "手动执行事务回滚，测试未执行"));
        }
        return result;
    }

    /** 启动恢复：平台重启后，排队中/执行中的测试都失去了执行者。 */
    public int recoverInterruptedRuns() {
        int interrupted = 0;
        for (TestCaseRun run : runRepository.findActive()) {
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

    private List<TestCaseRunId> queueRuns(TaskId taskId) {
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != TaskStatus.DONE) {
            return List.of();
        }
        if (runRepository.existsByTask(taskId)) {
            return List.of();
        }
        List<TestCaseBinding> bindings = taskTestCaseRepository.findByTask(taskId);
        if (bindings.isEmpty()) {
            return List.of();
        }

        List<TestCaseRunId> queued = new ArrayList<>();
        for (TestCaseBinding binding : bindings) {
            TestCaseRun run = TestCaseRun.queueForTask(taskId, task.getProjectId(), binding);
            if (!binding.executable()) {
                run.skip("测试用例已停用或删除，本次跳过");
            }
            runRepository.save(run);
            if (run.getStatus() == TestCaseRunStatus.QUEUED) {
                queued.add(run.getId());
            }
        }
        return List.copyOf(queued);
    }

    private void executeQueued(List<TestCaseRunId> runIds) {
        for (TestCaseRunId runId : runIds) {
            TestCaseRun run = runRepository.findById(runId).orElse(null);
            if (run == null || run.getStatus() != TestCaseRunStatus.QUEUED) {
                continue;
            }
            ProjectProfile project = projectProfileRepository.findById(run.getProjectId()).orElse(null);
            if (project == null) {
                run.begin();
                run.finish(new TestCaseExecutionResult(
                        TestCaseRunStatus.FAILED,
                        null,
                        "项目已不存在，测试未执行",
                        "执行所属项目已不存在：" + run.getProjectId().value()));
                runRepository.save(run);
                continue;
            }
            EnvironmentContext environment = EnvironmentContext.resolve(
                    environmentEntryRepository.findApplicableTo(project.ownerId(), project.id()));
            run.begin();
            runRepository.save(run);

            TestCaseExecutionResult result;
            try {
                result = testCaseExecutor.execute(new TestCaseExecutionRequest(
                        run.getTaskId().orElse(null),
                        run.getTestCaseId(),
                        run.getType(),
                        run.getCommand(),
                        run.getScriptFilename(),
                        run.getScriptContent(),
                        project.path(),
                        environment.values()));
            } catch (RuntimeException e) {
                result = new TestCaseExecutionResult(
                        TestCaseRunStatus.FAILED,
                        null,
                        "测试执行异常：" + messageOf(e),
                        e.getClass().getSimpleName() + ": " + messageOf(e));
            }
            run.finish(result);
            runRepository.save(run);
        }
    }

    private TestCaseManualRunResult createManualRuns(List<TestCaseId> testCaseIds) {
        List<TestCaseRun> runs = new ArrayList<>();
        List<TestCaseManualRunResult.Failure> failures = new ArrayList<>();
        for (TestCaseId testCaseId : testCaseIds) {
            try {
                TestCase testCase = testCaseRepository.findById(testCaseId)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "测试用例不存在：" + testCaseId.value()));
                if (!testCase.isEnabled()) {
                    throw new DomainException(
                            "测试用例已停用，不能执行：" + testCase.getName());
                }
                TestCaseRun run = TestCaseRun.queueManual(
                        testCase.getProjectId(),
                        testCase.snapshot(Instant.now()));
                runRepository.save(run);
                runs.add(run);
            } catch (RuntimeException e) {
                failures.add(new TestCaseManualRunResult.Failure(
                        testCaseId, messageOf(e)));
            }
        }
        return new TestCaseManualRunResult(runs, failures);
    }

    private void submitManual(List<TestCaseRunId> queued) {
        try {
            workers.execute(() -> executeQueued(queued));
        } catch (RuntimeException e) {
            interruptRuns(queued, "测试工作线程未能启动");
        }
    }

    private void interruptRuns(List<TestCaseRunId> runIds, String reason) {
        for (TestCaseRunId runId : runIds) {
            try {
                TestCaseRun run = runRepository.findById(runId).orElse(null);
                if (run != null && !run.getStatus().isFinished()) {
                    run.interrupt(reason);
                    runRepository.save(run);
                }
            } catch (RuntimeException ignored) {
                // 恢复失败留给下一次启动或人工排查
            }
        }
    }

    private static String messageOf(RuntimeException e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }

    private static List<TestCaseId> normalizeManualIds(List<TestCaseId> testCaseIds) {
        if (testCaseIds == null || testCaseIds.isEmpty()) {
            throw new DomainException("至少选择一个测试用例");
        }
        LinkedHashSet<TestCaseId> unique = new LinkedHashSet<>(testCaseIds);
        if (unique.contains(null)) {
            throw new DomainException("测试用例 ID 不能为空");
        }
        if (unique.size() > MAX_MANUAL_BATCH) {
            throw new DomainException(
                    "一次最多手动执行 " + MAX_MANUAL_BATCH + " 个测试用例");
        }
        return List.copyOf(unique);
    }
}
