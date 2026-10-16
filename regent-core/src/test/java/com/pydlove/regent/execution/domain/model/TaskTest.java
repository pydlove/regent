package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务状态机测试。
 *
 * <p>覆盖的不只是「正常路径能走通」，更重要的是<b>每一条非法迁移都被挡住</b>：
 * 状态机的价值全部体现在拒绝上，能让非法迁移悄悄通过的守卫等于没有守卫。
 */
class TaskTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);
    private static final DecisionId DECISION = DecisionId.of(9001L);

    private Task pendingTask() {
        return Task.manual(PROJECT, "补一个健康检查", "给 /health 加数据库连通性检查", 0);
    }

    private Task runningTask() {
        Task task = pendingTask();
        task.dispatch("codex/task-1");
        return task;
    }

    private Task blockedTask() {
        Task task = runningTask();
        task.block(DECISION);
        return task;
    }

    @Nested
    @DisplayName("创建")
    class Creation {

        @Test
        void 人工任务从待执行开始且没有上游引用() {
            Task task = pendingTask();

            assertEquals(TaskStatus.PENDING, task.getStatus());
            assertEquals(TaskSource.MANUAL, task.getSource());
            assertTrue(task.getRequirementId().isEmpty());
            assertTrue(task.getBugId().isEmpty());
            assertEquals(0, task.getAttemptCount());
            assertTrue(task.getStartedAt().isEmpty());
        }

        @Test
        void 需求任务必须携带需求引用() {
            Task task = Task.fromRequirement(PROJECT, RequirementId.of(7L), "拆解需求", "按需求文档实现", 10);

            assertEquals(TaskSource.REQUIREMENT, task.getSource());
            assertEquals(RequirementId.of(7L), task.getRequirementId().orElseThrow());
            assertTrue(task.getBugId().isEmpty());
        }

        @Test
        void 缺陷任务必须携带缺陷引用() {
            Task task = Task.fromBug(PROJECT, BugId.of(3L), "修复空指针", "修 login 的空指针", 5);

            assertEquals(TaskSource.BUG_FIX, task.getSource());
            assertEquals(BugId.of(3L), task.getBugId().orElseThrow());
            assertTrue(task.getRequirementId().isEmpty());
        }

        @Test
        void 来源与引用不一致时拒绝创建() {
            assertThrows(DomainException.class,
                    () -> Task.fromRequirement(PROJECT, null, "标题", "指令", 0));
            assertThrows(DomainException.class,
                    () -> Task.fromBug(PROJECT, null, "标题", "指令", 0));
        }

        @Test
        void 标题与指令不能为空且标题有长度上限() {
            assertThrows(DomainException.class, () -> Task.manual(PROJECT, "  ", "指令", 0));
            assertThrows(DomainException.class, () -> Task.manual(PROJECT, "标题", "", 0));
            assertThrows(DomainException.class,
                    () -> Task.manual(PROJECT, "标".repeat(257), "指令", 0));
        }
    }

    @Nested
    @DisplayName("编辑")
    class Editing {

        @Test
        void 非完成任务可以修改标题指令和优先级() {
            Task task = pendingTask();

            task.edit("新的标题", "新的执行指令", 8);

            assertEquals("新的标题", task.getTitle());
            assertEquals("新的执行指令", task.getPrompt());
            assertEquals(8, task.getPriority());
        }

        @Test
        void 执行中阻塞中失败和终止的任务允许编辑() {
            Task running = runningTask();
            Task blocked = blockedTask();
            Task failed = runningTask();
            failed.fail();
            Task cancelled = pendingTask();
            cancelled.cancel();

            assertDoesNotThrow(() -> running.edit("运行中标题", "运行中指令", 1));
            assertDoesNotThrow(() -> blocked.edit("阻塞中标题", "阻塞中指令", 2));
            assertDoesNotThrow(() -> failed.edit("失败标题", "失败指令", 3));
            assertDoesNotThrow(() -> cancelled.edit("终止标题", "终止指令", 4));
        }

        @Test
        void 已完成的任务不能编辑() {
            Task task = runningTask();
            task.complete();

            DomainException error = assertThrows(DomainException.class,
                    () -> task.edit("不能改", "也不能改", 9));

            assertEquals("已完成的任务不能编辑", error.getMessage());
            assertEquals("补一个健康检查", task.getTitle());
            assertEquals("给 /health 加数据库连通性检查", task.getPrompt());
            assertEquals(0, task.getPriority());
        }

        @Test
        void 编辑仍校验标题与指令() {
            assertThrows(DomainException.class, () -> pendingTask().edit("  ", "指令", 0));
            assertThrows(DomainException.class, () -> pendingTask().edit("标题", "  ", 0));
            assertThrows(DomainException.class,
                    () -> pendingTask().edit("标".repeat(257), "指令", 0));
        }
    }

    @Nested
    @DisplayName("派发与完成")
    class DispatchAndCompletion {

        @Test
        void 排队只表示已提交执行位且不算一次尝试() {
            Task task = pendingTask();

            task.queue();

            assertEquals(TaskStatus.QUEUED, task.getStatus());
            assertEquals(0, task.getAttemptCount());
            assertTrue(task.getStartedAt().isEmpty());
            assertTrue(task.getBranchName().isEmpty());
        }

        @Test
        void 派发把任务推入执行中并记一次尝试() {
            Task task = pendingTask();

            task.dispatch("codex/task-1");

            assertEquals(TaskStatus.RUNNING, task.getStatus());
            assertEquals("codex/task-1", task.getBranchName().orElseThrow());
            assertEquals(1, task.getAttemptCount());
            assertTrue(task.getStartedAt().isPresent());
        }

        @Test
        void 排队中的任务取得执行位后进入执行中() {
            Task task = pendingTask();
            task.queue();

            task.dispatch("codex/task-1");

            assertEquals(TaskStatus.RUNNING, task.getStatus());
            assertEquals("codex/task-1", task.getBranchName().orElseThrow());
            assertEquals(1, task.getAttemptCount());
            assertTrue(task.getStartedAt().isPresent());
        }

        @Test
        void 重复派发被拒绝() {
            Task task = runningTask();

            assertThrows(DomainException.class, () -> task.dispatch("codex/task-1"));
        }

        @Test
        void 完成后进入终态且不能再终止() {
            Task task = runningTask();

            task.complete();

            assertEquals(TaskStatus.DONE, task.getStatus());
            assertTrue(task.getFinishedAt().isPresent());
            assertTrue(task.getStatus().isTerminal());
            assertThrows(DomainException.class, task::cancel);
            assertThrows(DomainException.class, task::retry);
        }

        @Test
        void 待执行的任务不能直接完成或失败() {
            Task task = pendingTask();

            assertThrows(DomainException.class, task::complete);
            assertThrows(DomainException.class, task::fail);
        }

        @Test
        void 执行线程只在执行中可记录() {
            Task task = runningTask();
            task.recordThreadId("thread-abc");

            assertEquals("thread-abc", task.getCodexThreadId().orElseThrow());
            assertThrows(DomainException.class, () -> pendingTask().recordThreadId("thread-abc"));
        }
    }

    @Nested
    @DisplayName("阻塞与续跑")
    class Blocking {

        @Test
        void 阻塞记录决策引用续跑后清空() {
            Task task = runningTask();

            task.block(DECISION);
            assertEquals(TaskStatus.BLOCKED, task.getStatus());
            assertEquals(DECISION, task.getBlockingDecisionId().orElseThrow());

            task.resume();
            assertEquals(TaskStatus.RUNNING, task.getStatus());
            assertTrue(task.getBlockingDecisionId().isEmpty());
        }

        @Test
        void 只有执行中的任务可以阻塞() {
            assertThrows(DomainException.class, () -> pendingTask().block(DECISION));
            assertThrows(DomainException.class, () -> blockedTask().block(DECISION));
        }

        @Test
        void 只有阻塞中的任务可以续跑() {
            assertThrows(DomainException.class, () -> pendingTask().resume());
            assertThrows(DomainException.class, () -> runningTask().resume());
        }

        @Test
        void 阻塞时不能直接完成() {
            assertThrows(DomainException.class, () -> blockedTask().complete());
        }
    }

    @Nested
    @DisplayName("失败、重试与重新执行")
    class FailureAndRetry {

        @Test
        void 失败可重试回待执行并保留尝试次数() {
            Task task = runningTask();
            task.fail();
            assertEquals(TaskStatus.FAILED, task.getStatus());
            assertTrue(task.getStartedAt().isPresent());

            task.retry();

            assertEquals(TaskStatus.PENDING, task.getStatus());
            assertEquals(1, task.getAttemptCount());
            assertTrue(task.getStartedAt().isEmpty());
            assertTrue(task.getFinishedAt().isEmpty());
        }

        @Test
        void 重试后再次派发尝试次数累加() {
            Task task = runningTask();
            task.fail();
            task.retry();

            task.dispatch("codex/task-1");

            assertEquals(2, task.getAttemptCount());
        }

        @Test
        void 已终止任务可重新执行回待执行并保留尝试次数() {
            Task task = runningTask();
            task.cancel();

            task.retry();

            assertEquals(TaskStatus.PENDING, task.getStatus());
            assertEquals(1, task.getAttemptCount());
            assertTrue(task.getStartedAt().isEmpty());
            assertTrue(task.getFinishedAt().isEmpty());
        }

        @Test
        void 只有失败或已终止的任务可以重试() {
            assertThrows(DomainException.class, () -> pendingTask().retry());
            assertThrows(DomainException.class, () -> runningTask().retry());
            assertThrows(DomainException.class, () -> blockedTask().retry());

            Task done = runningTask();
            done.complete();
            assertThrows(DomainException.class, done::retry);
        }
    }

    @Nested
    @DisplayName("终止")
    class Cancellation {

        @Test
        void 待执行执行中阻塞中失败后都可终止() {
            assertDoesNotThrow(() -> pendingTask().cancel());
            Task queued = pendingTask();
            queued.queue();
            assertDoesNotThrow(queued::cancel);
            assertDoesNotThrow(() -> runningTask().cancel());
            assertDoesNotThrow(() -> blockedTask().cancel());

            Task failed = runningTask();
            failed.fail();
            assertDoesNotThrow(failed::cancel);
        }

        @Test
        void 终止时清空阻塞引用并记录结束时间() {
            Task task = blockedTask();

            task.cancel();

            assertEquals(TaskStatus.CANCELLED, task.getStatus());
            assertTrue(task.getBlockingDecisionId().isEmpty());
            assertTrue(task.getFinishedAt().isPresent());
        }

        @Test
        void 已终止的任务不能重复终止() {
            Task task = pendingTask();
            task.cancel();

            assertThrows(DomainException.class, task::cancel);
        }
    }

    @Nested
    @DisplayName("删除")
    class Deletion {

        @Test
        void 只有待执行或排队中的任务可以删除() {
            assertDoesNotThrow(() -> pendingTask().requireDeletable());
            Task queued = pendingTask();
            queued.queue();
            assertDoesNotThrow(queued::requireDeletable);

            Task running = runningTask();
            Task blocked = blockedTask();
            Task failed = runningTask();
            failed.fail();
            Task done = runningTask();
            done.complete();
            Task cancelled = pendingTask();
            cancelled.cancel();

            assertThrows(DomainException.class, running::requireDeletable);
            assertThrows(DomainException.class, blocked::requireDeletable);
            assertThrows(DomainException.class, failed::requireDeletable);
            assertThrows(DomainException.class, done::requireDeletable);
            assertThrows(DomainException.class, cancelled::requireDeletable);
        }
    }

    @Nested
    @DisplayName("持久化重建")
    class Persistence {

        @Test
        void 身份只能分配一次() {
            Task task = pendingTask();
            assertThrows(DomainException.class, () -> task.assignIdentity(null));

            task.assignIdentity(TaskId.of(42L));
            assertEquals(TaskId.of(42L), task.getId());

            assertThrows(DomainException.class, () -> task.assignIdentity(TaskId.of(43L)));
        }

        @Test
        void 从快照重建后状态与时间戳一致() {
            Instant created = Instant.parse("2026-10-16T02:00:00Z");
            Instant started = Instant.parse("2026-10-16T02:05:00Z");
            TaskSnapshot snapshot = new TaskSnapshot(
                    TaskId.of(42L), PROJECT, RequirementId.of(7L), null, TaskSource.REQUIREMENT,
                    "标题", "指令", 3, TaskStatus.RUNNING,
                    "thread-1", "codex/task-42", null, 2,
                    started, null, created, started);

            Task task = Task.restore(snapshot);

            assertEquals(TaskId.of(42L), task.getId());
            assertEquals(TaskStatus.RUNNING, task.getStatus());
            assertEquals(2, task.getAttemptCount());
            assertEquals(created, task.getCreatedAt());
            assertEquals(started, task.getUpdatedAt());
            assertNotNull(task.getRequirementId().orElse(null));
        }

        @Test
        void 重建出的对象仍然受状态机约束() {
            Instant now = Instant.parse("2026-10-16T02:00:00Z");
            Task restored = Task.restore(new TaskSnapshot(
                    TaskId.of(42L), PROJECT, null, null, TaskSource.MANUAL,
                    "标题", "指令", 0, TaskStatus.DONE,
                    null, null, null, 1, now, now, now, now));

            assertThrows(DomainException.class, () -> restored.dispatch("codex/task-42"));
            assertThrows(DomainException.class, restored::retry);
        }
    }

    @Nested
    @DisplayName("状态解析")
    class Parsing {

        @Test
        void 大小写宽容但拼错拒绝() {
            assertEquals(TaskStatus.BLOCKED, TaskStatus.parse("blocked"));
            assertEquals(TaskStatus.PENDING, TaskStatus.parse(" Pending "));
            assertEquals(TaskStatus.QUEUED, TaskStatus.parse("queued"));
            assertThrows(DomainException.class, () -> TaskStatus.parse("FAIL"));
            assertThrows(DomainException.class, () -> TaskStatus.parse(null));
        }
    }
}
