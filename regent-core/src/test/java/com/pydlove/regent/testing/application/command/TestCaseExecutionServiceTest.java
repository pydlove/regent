package com.pydlove.regent.testing.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import com.pydlove.regent.support.TestingFakes;
import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseBinding;
import com.pydlove.regent.testing.domain.model.TestCaseExecutionRequest;
import com.pydlove.regent.testing.domain.model.TestCaseExecutionResult;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseRun;
import com.pydlove.regent.testing.domain.model.TestCaseRunStatus;
import com.pydlove.regent.testing.domain.model.TestCaseRunTrigger;
import com.pydlove.regent.testing.domain.model.TestCaseType;
import com.pydlove.regent.testing.domain.port.TestCaseExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCaseExecutionServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);

    private ExecutionFakes.Tasks tasks;
    private TestingFakes.Bindings bindings;
    private TestingFakes.TestCases testCases;
    private TestingFakes.Runs runs;
    private ScriptedExecutor executor;
    private TestCaseExecutionService service;
    private Task completedTask;

    @BeforeEach
    void setUp() {
        tasks = new ExecutionFakes.Tasks();
        bindings = new TestingFakes.Bindings();
        testCases = new TestingFakes.TestCases();
        runs = new TestingFakes.Runs();
        executor = new ScriptedExecutor();

        ExecutionFakes.Projects projects = new ExecutionFakes.Projects();
        projects.put(ExecutionFakes.Projects.profile(1L, DecisionLevel.L1, true));
        service = new TestCaseExecutionService(
                tasks,
                projects,
                new ExecutionFakes.Environments(),
                bindings,
                testCases,
                runs,
                executor,
                new TestingFakes.DirectExecutorService(),
                new ExecutionFakes.Transactions());

        completedTask = Task.manual(PROJECT, "完成页面", "实现页面", 0);
        tasks.save(completedTask);
        completedTask.dispatch("codex/task-1");
        completedTask.complete();
        tasks.save(completedTask);
    }

    @Test
    @DisplayName("任务完成后按绑定创建并执行测试记录")
    void executesBoundCases() {
        bindings.put(completedTask.getId(), binding(2L, "登录页回归", true));
        executor.result = new TestCaseExecutionResult(
                TestCaseRunStatus.SUCCESS, 0, "通过", "ok");

        service.scheduleAfterTaskCompleted(completedTask.getId());

        assertEquals(1, runs.all().size());
        assertEquals(TestCaseRunStatus.SUCCESS, runs.all().get(0).getStatus());
        assertEquals(1, executor.requests.size());
        assertEquals("/tmp/regent-project-1", executor.requests.get(0).projectPath());
    }

    @Test
    @DisplayName("测试失败只记录失败，不抛异常影响任务结算")
    void recordsFailureWithoutThrowing() {
        bindings.put(completedTask.getId(), binding(2L, "接口冒烟", true));
        executor.result = new TestCaseExecutionResult(
                TestCaseRunStatus.FAILED, 2, "失败", "boom");

        service.scheduleAfterTaskCompleted(completedTask.getId());

        assertEquals(TestCaseRunStatus.FAILED, runs.all().get(0).getStatus());
        assertEquals(2, runs.all().get(0).getExitCode());
    }

    @Test
    @DisplayName("已停用用例留下跳过记录且不启动进程")
    void skipsDisabledBinding() {
        bindings.put(completedTask.getId(), binding(2L, "已停用", false));

        service.scheduleAfterTaskCompleted(completedTask.getId());

        assertEquals(TestCaseRunStatus.SKIPPED, runs.all().get(0).getStatus());
        assertEquals(0, executor.requests.size());
    }

    @Test
    @DisplayName("启动恢复会把未收场测试标记为中断")
    void recoversActiveRuns() {
        TestCaseRun queued = TestCaseRun.queueForTask(
                completedTask.getId(), PROJECT, binding(2L, "登录页回归", true));
        runs.save(queued);

        int interrupted = service.recoverInterruptedRuns();

        assertEquals(1, interrupted);
        assertEquals(TestCaseRunStatus.INTERRUPTED, runs.all().get(0).getStatus());
    }

    @Test
    @DisplayName("菜单手动执行不依赖任务，记录触发类型")
    void runsCaseManually() {
        TestCase testCase = testCases.put(TestCase.create(
                PROJECT,
                "手动冒烟",
                TestCaseType.API,
                "手动执行",
                "echo ok",
                null,
                null,
                true));
        executor.result = new TestCaseExecutionResult(
                TestCaseRunStatus.SUCCESS, 0, "通过", "ok");

        var result = service.runMany(List.of(testCase.getId()));

        assertEquals(1, result.runs().size());
        assertEquals(TestCaseRunTrigger.MANUAL, runs.all().get(0).getTrigger());
        assertTrue(runs.all().get(0).getTaskId().isEmpty());
        assertEquals(TestCaseRunStatus.SUCCESS, runs.all().get(0).getStatus());
    }

    private static TestCaseBinding binding(long id, String name, boolean enabled) {
        return new TestCaseBinding(
                TestCaseId.of(id),
                name,
                TestCaseType.WEB,
                "echo ok",
                null,
                null,
                null,
                enabled,
                false,
                Instant.parse("2026-10-17T00:00:00Z"));
    }

    private static final class ScriptedExecutor implements TestCaseExecutor {

        private final List<TestCaseExecutionRequest> requests = new ArrayList<>();
        private TestCaseExecutionResult result = new TestCaseExecutionResult(
                TestCaseRunStatus.SUCCESS, 0, "通过", "ok");

        @Override
        public TestCaseExecutionResult execute(TestCaseExecutionRequest request) {
            requests.add(request);
            return result;
        }
    }
}
