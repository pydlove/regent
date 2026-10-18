package com.pydlove.regent.testing.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCaseRunTest {

    private static final TaskId TASK = TaskId.of(10L);
    private static final ProjectId PROJECT = ProjectId.of(1L);
    private static final TestCaseBinding BINDING = new TestCaseBinding(
            TestCaseId.of(2L),
            "登录页回归",
            TestCaseType.WEB,
            "node \"$REGENT_TEST_FILE\"",
            "login.js",
            "console.log('ok')",
            null,
            true,
            false,
            Instant.parse("2026-10-17T00:00:00Z"));

    @Test
    @DisplayName("排队、开始、成功是一条完整迁移")
    void finishesSuccessfully() {
        TestCaseRun run = TestCaseRun.queueForTask(TASK, PROJECT, BINDING);
        assertEquals(TestCaseRunStatus.QUEUED, run.getStatus());

        run.begin();
        run.finish(new TestCaseExecutionResult(TestCaseRunStatus.SUCCESS, 0, "通过", "ok"));

        assertEquals(TestCaseRunStatus.SUCCESS, run.getStatus());
        assertEquals(0, run.getExitCode());
        assertEquals("ok", run.getOutput());
    }

    @Test
    @DisplayName("排队中的测试不能直接写结论")
    void cannotFinishBeforeStarting() {
        TestCaseRun run = TestCaseRun.queueForTask(TASK, PROJECT, BINDING);

        assertThrows(DomainException.class, () -> run.finish(
                new TestCaseExecutionResult(TestCaseRunStatus.SUCCESS, 0, "通过", "ok")));
    }

    @Test
    @DisplayName("排队中的测试可以因用例停用而跳过")
    void skipsQueuedRun() {
        TestCaseRun run = TestCaseRun.queueForTask(TASK, PROJECT, BINDING);

        run.skip("已停用");

        assertEquals(TestCaseRunStatus.SKIPPED, run.getStatus());
    }

    @Test
    @DisplayName("重启会中断未收场记录")
    void interruptsActiveRun() {
        TestCaseRun run = TestCaseRun.queueForTask(TASK, PROJECT, BINDING);
        run.begin();

        run.interrupt("平台重启");

        assertEquals(TestCaseRunStatus.INTERRUPTED, run.getStatus());
    }

    @Test
    @DisplayName("菜单手动执行没有任务号，但保留项目归属")
    void queuesManualRunWithoutTask() {
        TestCaseRun run = TestCaseRun.queueManual(PROJECT, BINDING);

        assertEquals(TestCaseRunTrigger.MANUAL, run.getTrigger());
        assertEquals(PROJECT, run.getProjectId());
        assertTrue(run.getTaskId().isEmpty());
    }
}
