package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.shared.kernel.TokenUsage;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRunQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T02:00:00Z");

    private final ExecutionFakes.Runs runs = new ExecutionFakes.Runs();
    private final ExecutionFakes.Events events = new ExecutionFakes.Events();
    private TaskRunQueryService service;

    @BeforeEach
    void setUp() {
        events.wire(runs);
        service = new TaskRunQueryService(
                runs, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Codex 缺失诊断给出可重试与换设备建议并脱敏摘要")
    void diagnosesCodexUnavailable() {
        TaskRun run = TaskRun.start(TaskId.of(7L), 1, "main");
        run.finish(new ExecResult(
                RunStatus.FAILED,
                1,
                "thread-1",
                TokenUsage.ZERO,
                "Codex 未安装，password=super-secret token=sk-123456789",
                null,
                true,
                List.of(),
                List.of(),
                null));
        runs.save(run);
        events.append(run.getId(), new RunEvent(
                0, "agent.claim", "{}", NOW.minusSeconds(20)));
        events.append(run.getId(), new RunEvent(
                1, "thread.started", "{}", NOW.minusSeconds(10)));

        ExecutionDiagnostic diagnostic = service.diagnose(run.getId());

        assertEquals("CODEX_UNAVAILABLE", diagnostic.failureCategory());
        assertEquals("FAILED", diagnostic.healthStatus());
        assertEquals("执行会话", diagnostic.failureStage());
        assertEquals("已建立执行会话", diagnostic.lastSuccessStage());
        assertTrue(diagnostic.retryable());
        assertTrue(diagnostic.requiresDeviceChange());
        assertFalse(diagnostic.summary().contains("super-secret"));
        assertFalse(diagnostic.summary().contains("sk-123456789"));
    }

    @Test
    @DisplayName("读不懂出参被标为不可直接重试")
    void invalidOutputIsNotRetryable() {
        TaskRun run = TaskRun.start(TaskId.of(7L), 1, "main");
        run.finish(new ExecResult(
                RunStatus.SUCCESS, 0, "thread-1", TokenUsage.ZERO,
                "输出不合约定", "{}", false, List.of(), List.of(), null));
        runs.save(run);

        ExecutionDiagnostic diagnostic = service.diagnose(run.getId());

        assertEquals("INVALID_OUTPUT", diagnostic.failureCategory());
        assertFalse(diagnostic.retryable());
    }

    @Test
    @DisplayName("租约过期诊断设备失联")
    void expiredLeaseIsDeviceLost() {
        TaskRun run = TaskRun.start(TaskId.of(7L), 1, "main");
        run.claimBy("agent@test", "device-1", NOW.minusSeconds(1));
        runs.save(run);

        ExecutionDiagnostic diagnostic = service.diagnose(run.getId());

        assertEquals("DEVICE_LOST", diagnostic.healthStatus());
    }
}
