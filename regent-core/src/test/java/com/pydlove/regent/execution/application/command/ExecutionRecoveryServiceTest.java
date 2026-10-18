package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 启动恢复：进程死了，任务不能跟着永远停在执行中。 */
class ExecutionRecoveryServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);
    private static final TaskId TASK = TaskId.of(1L);

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Runs runs = new ExecutionFakes.Runs();
    private final ExecutionFakes.Transactions transactions = new ExecutionFakes.Transactions();

    private ExecutionRecoveryService service;

    @BeforeEach
    void setUp() {
        tasks.wire(runs);
        service = new ExecutionRecoveryService(tasks, runs, transactions);
    }

    @Test
    @DisplayName("内部执行遗留 RUNNING：记录收敛为已中断，任务回到排队并保留会话")
    void 恢复内部孤儿执行() {
        Task task = ExecutionFakes.withThread(
                ExecutionFakes.task(1L, PROJECT, TaskStatus.RUNNING), "th-89");
        tasks.add(task);
        TaskRun run = TaskRun.start(TASK, 1, "codex/task-1");
        runs.save(run);

        ExecutionRecoveryService.ExecutionRecoveryResult result = service.recoverInterruptedExecutions();

        assertEquals(RunStatus.INTERRUPTED, runs.get(run.getId().value()).getStatus());
        assertTrue(runs.get(run.getId().value()).getSummary().contains("平台重启"));
        assertEquals(TaskStatus.QUEUED, tasks.get(1L).getStatus());
        assertEquals("th-89", tasks.get(1L).getCodexThreadId().orElseThrow(),
                "续跑要靠原来的 thread_id，恢复时不能清掉");
        assertEquals(1, tasks.get(1L).getAttemptCount(), "重新排队不是一次新的执行尝试");
        assertEquals(1, result.interruptedRuns());
        assertEquals(1, result.requeuedTasks());
    }

    @Test
    @DisplayName("优雅关闭已写入 INTERRUPTED：下次启动仍会把任务重新排队")
    void 恢复已标记中断的执行() {
        tasks.add(ExecutionFakes.task(1L, PROJECT, TaskStatus.RUNNING));
        TaskRun run = TaskRun.start(TASK, 1, "codex/task-1");
        run.interrupt("平台正在重启");
        runs.save(run);

        ExecutionRecoveryService.ExecutionRecoveryResult result = service.recoverInterruptedExecutions();

        assertEquals(TaskStatus.QUEUED, tasks.get(1L).getStatus());
        assertEquals(0, result.interruptedRuns(), "已经收敛的记录不重复计数");
        assertEquals(1, result.requeuedTasks());
    }

    @Test
    @DisplayName("已经停在 BLOCKED 等批准的记录不能被当成重启中断")
    void 等待决策的任务保持不动() {
        tasks.add(ExecutionFakes.task(1L, PROJECT, TaskStatus.RUNNING));
        TaskRun run = TaskRun.start(TASK, 1, "codex/task-1");
        run.finish(ExecutionFakes.Executor.blocked(DecisionLevel.L2, false));
        runs.save(run);

        ExecutionRecoveryService.ExecutionRecoveryResult result = service.recoverInterruptedExecutions();

        assertEquals(RunStatus.BLOCKED, runs.get(run.getId().value()).getStatus());
        assertEquals(TaskStatus.RUNNING, tasks.get(1L).getStatus(), "批准后续跑由正常派发链路接管");
        assertFalse(result.hasChanges());
    }

    @Test
    @DisplayName("外部执行者持有租约时，平台重启不替它判定生死")
    void 外部执行保持不动() {
        tasks.add(ExecutionFakes.task(1L, PROJECT, TaskStatus.RUNNING));
        TaskRun run = TaskRun.start(TASK, 1, "codex/task-1");
        run.claimBy("codex-desktop", Instant.now().plusSeconds(600));
        runs.save(run);

        ExecutionRecoveryService.ExecutionRecoveryResult result = service.recoverInterruptedExecutions();

        assertEquals(RunStatus.RUNNING, runs.get(run.getId().value()).getStatus());
        assertEquals(TaskRun.EXECUTOR_AGENT, runs.get(run.getId().value()).getExecutor());
        assertEquals(TaskStatus.RUNNING, tasks.get(1L).getStatus());
        assertFalse(result.hasChanges());
    }

    @Test
    @DisplayName("连续重启留下多条 RUNNING：全部收敛，任务只重新排队一次")
    void 清理同一任务的多个遗留执行() {
        tasks.add(ExecutionFakes.task(1L, PROJECT, TaskStatus.RUNNING));
        TaskRun first = TaskRun.start(TASK, 1, "codex/task-1");
        TaskRun second = TaskRun.start(TASK, 2, "codex/task-1");
        runs.save(first);
        runs.save(second);

        ExecutionRecoveryService.ExecutionRecoveryResult result = service.recoverInterruptedExecutions();

        assertEquals(RunStatus.INTERRUPTED, runs.get(first.getId().value()).getStatus());
        assertEquals(RunStatus.INTERRUPTED, runs.get(second.getId().value()).getStatus());
        assertEquals(TaskStatus.QUEUED, tasks.get(1L).getStatus());
        assertEquals(2, result.interruptedRuns());
        assertEquals(1, result.requeuedTasks());
    }

    @Test
    @DisplayName("重复执行恢复是幂等的")
    void 重复恢复不重复排队() {
        tasks.add(ExecutionFakes.task(1L, PROJECT, TaskStatus.RUNNING));
        runs.save(TaskRun.start(TASK, 1, "codex/task-1"));

        service.recoverInterruptedExecutions();
        ExecutionRecoveryService.ExecutionRecoveryResult second = service.recoverInterruptedExecutions();

        assertFalse(second.hasChanges());
        assertTrue(second.failures().isEmpty());
    }

    @Test
    @DisplayName("任务已经收场时只清理遗留执行，不把任务重新拉起来")
    void 不重开已收场任务() {
        tasks.add(ExecutionFakes.task(1L, PROJECT, TaskStatus.DONE));
        TaskRun run = TaskRun.start(TASK, 1, "codex/task-1");
        runs.save(run);

        ExecutionRecoveryService.ExecutionRecoveryResult result = service.recoverInterruptedExecutions();

        assertEquals(RunStatus.INTERRUPTED, runs.get(run.getId().value()).getStatus());
        assertEquals(TaskStatus.DONE, tasks.get(1L).getStatus());
        assertEquals(0, result.requeuedTasks());
    }
}
