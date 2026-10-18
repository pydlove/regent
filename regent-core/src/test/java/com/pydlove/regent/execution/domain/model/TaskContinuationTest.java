package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 续跑（决策批准后接着上一轮会话跑）的状态守卫。
 *
 * <p>它和 {@code dispatch} 长得像，但入口状态与凭据都不同，守卫松一格就会出现
 * 「没有会话却要续跑」或者「还没批准就自己又跑一遍」。
 */
class TaskContinuationTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);

    private Task runningTaskWithThread() {
        Task task = Task.manual(PROJECT, "补测试", "给导出功能补测试", 0);
        task.dispatch("codex/task-1");
        task.recordThreadId("th-1");
        return task;
    }

    @Nested
    @DisplayName("合法入口")
    class Allowed {

        @Test
        void 开启续跑会把尝试次数加一并刷新开始时间() {
            Task task = runningTaskWithThread();
            int before = task.getAttemptCount();

            task.beginContinuation();

            assertEquals(before + 1, task.getAttemptCount());
            assertEquals(TaskStatus.RUNNING, task.getStatus());
            assertEquals("codex/task-1", task.getBranchName().orElseThrow(), "续跑要留在原分支上");
            assertTrue(task.getStartedAt().isPresent());
        }

        @Test
        void 已经阻塞过的任务被批准后可以续跑() {
            Task task = runningTaskWithThread();
            task.block(DecisionId.of(7L));
            task.resume();

            task.beginContinuation();

            assertEquals(2, task.getAttemptCount());
        }
    }

    @Nested
    @DisplayName("拒绝")
    class Rejected {

        @Test
        void 待执行的任务不能直接续跑() {
            Task task = Task.manual(PROJECT, "补测试", "给导出功能补测试", 0);

            assertThrows(DomainException.class, task::beginContinuation);
        }

        @Test
        void 阻塞中的任务不能续跑() {
            Task task = runningTaskWithThread();
            task.block(DecisionId.of(7L));

            assertThrows(DomainException.class, task::beginContinuation, "还没批准就想接着跑，等于把问题跳过去了");
        }

        @Test
        void 没有会话的任务不能续跑() {
            Task task = Task.manual(PROJECT, "补测试", "给导出功能补测试", 0);
            task.dispatch("codex/task-1");

            assertThrows(DomainException.class, task::beginContinuation);
        }
    }

    @Nested
    @DisplayName("重启恢复")
    class RestartRecovery {

        @Test
        void 执行中的任务回到排队且保留会话与尝试次数() {
            Task task = runningTaskWithThread();
            int attempts = task.getAttemptCount();

            task.requeueAfterInterruption();

            assertEquals(TaskStatus.QUEUED, task.getStatus());
            assertEquals("th-1", task.getCodexThreadId().orElseThrow());
            assertEquals("codex/task-1", task.getBranchName().orElseThrow());
            assertEquals(attempts, task.getAttemptCount(), "尝试次数由下一次 dispatch 统一增加");
            assertTrue(task.getStartedAt().isEmpty());
        }

        @Test
        void 没在执行的任意任务都不能走恢复入口() {
            Task pending = Task.manual(PROJECT, "补测试", "给导出功能补测试", 0);

            assertThrows(DomainException.class, pending::requeueAfterInterruption);
        }

        @Test
        void 等待决策的任务不能被重启恢复抢走() {
            Task task = runningTaskWithThread();
            task.block(DecisionId.of(7L));

            assertThrows(DomainException.class, task::requeueAfterInterruption);
        }
    }
}
