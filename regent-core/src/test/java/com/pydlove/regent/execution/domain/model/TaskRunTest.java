package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 执行记录的收敛规则测试。
 *
 * <p>重点在「什么时候不许写结论」：一行记录只收敛一次，是这张表作为审计证据的前提。
 */
class TaskRunTest {

    private static final TaskId TASK = TaskId.of(7L);

    private TaskRun running() {
        return TaskRun.start(TASK, 1, "codex/task-7");
    }

    private ExecResult success() {
        return new ExecResult(RunStatus.SUCCESS, 0, "th-1", TokenUsage.of(100, 20, 30),
                "做完了", "做完了", true, List.of("a.txt", "b/c.txt"), List.of(), null);
    }

    @Nested
    @DisplayName("开跑")
    class Start {

        @Test
        void 开跑时是进行中且没有结论() {
            TaskRun run = running();

            assertEquals(RunStatus.RUNNING, run.getStatus());
            assertFalse(run.getStatus().isFinished());
            assertEquals(TokenUsage.ZERO, run.getTokens());
            assertTrue(run.getChangedFiles().isEmpty());
            assertNull(run.getFinishedAt());
            assertNull(run.getId());
        }

        @Test
        void 尝试序号从1开始() {
            assertThrows(DomainException.class, () -> TaskRun.start(TASK, 0, "codex/task-7"));
        }

        @Test
        void 分支名不能为空() {
            assertThrows(DomainException.class, () -> TaskRun.start(TASK, 1, " "));
        }

        @Test
        void 主键只能分配一次() {
            TaskRun run = running();
            run.assignIdentity(TaskRunId.of(1L));

            assertEquals(TaskRunId.of(1L), run.getId());
            assertThrows(DomainException.class, () -> run.assignIdentity(TaskRunId.of(2L)));
        }
    }

    @Nested
    @DisplayName("收敛")
    class Finish {

        @Test
        void 成功收敛后带上token与改动文件() {
            TaskRun run = running();

            run.finish(success());

            assertEquals(RunStatus.SUCCESS, run.getStatus());
            assertEquals(0, run.getExitCode());
            assertEquals(TokenUsage.of(100, 20, 30), run.getTokens());
            assertEquals("做完了", run.getSummary());
            assertEquals(List.of("a.txt", "b/c.txt"), run.getChangedFiles());
            assertTrue(run.isSchemaValid());
            assertEquals(false, run.getFinishedAt() == null);
        }

        @Test
        void 出参不合法时按失败收敛() {
            TaskRun run = running();
            ExecResult broken = new ExecResult(RunStatus.SUCCESS, 0, "th-1", TokenUsage.ZERO,
                    null, "{\"没按约定来\":true}", false, List.of(), List.of(), null);

            run.finish(broken);

            assertEquals(RunStatus.FAILED, run.getStatus(), "出参读不懂时不能算成功，否则摘要与决策全是空的");
            assertFalse(run.isSchemaValid());
        }

        @Test
        void 阻塞收敛要保留结论() {
            TaskRun run = running();
            ExecResult blocked = new ExecResult(RunStatus.BLOCKED, 0, "th-1", TokenUsage.of(1, 0, 1),
                    "停下来问一句", null, true, List.of(), List.of(),
                    new ExecDecision(com.pydlove.regent.shared.kernel.DecisionLevel.L2, "要不要动依赖？",
                            List.of("动"), "不动", true, "构建"));

            run.finish(blocked);

            assertEquals(RunStatus.BLOCKED, run.getStatus());
            assertTrue(run.getStatus().isFinished(), "阻塞是带结论的暂停，记录本身已经收敛");
        }

        @Test
        void 重复收敛被拒绝() {
            TaskRun run = running();
            run.finish(success());

            assertThrows(DomainException.class, () -> run.finish(success()));
        }

        @Test
        void 结果不能是进行中() {
            assertThrows(DomainException.class, () -> new ExecResult(RunStatus.RUNNING, 0, "th-1",
                    TokenUsage.ZERO, "还在跑", null, true, List.of(), List.of(), null));
        }

        @Test
        void 阻塞结果必须带上待决策问题() {
            assertThrows(DomainException.class, () -> new ExecResult(RunStatus.BLOCKED, 0, "th-1",
                    TokenUsage.ZERO, "停下来了", null, true, List.of(), List.of(), null));
        }
    }

    @Test
    void 重建后字段一致() {
        TaskRunSnapshot snapshot = new TaskRunSnapshot(TaskRunId.of(3L), TASK, 2, "codex/task-7",
                RunStatus.BLOCKED, 0, TokenUsage.of(5, 1, 2), "等人工", "原文", true,
                List.of("x.txt"), TaskRun.EXECUTOR_INTERNAL, null, null,
                Instant.parse("2026-10-16T02:00:00Z"),
                Instant.parse("2026-10-16T02:05:00Z"), Instant.parse("2026-10-16T02:00:00Z"));

        TaskRun run = TaskRun.restore(snapshot);

        assertEquals(TaskRunId.of(3L), run.getId());
        assertEquals(2, run.getAttemptNo());
        assertEquals(RunStatus.BLOCKED, run.getStatus());
        assertEquals(TokenUsage.of(5, 1, 2), run.getTokens());
        assertEquals(List.of("x.txt"), run.getChangedFiles());
        assertEquals(Instant.parse("2026-10-16T02:05:00Z"), run.getFinishedAt());
    }
}
