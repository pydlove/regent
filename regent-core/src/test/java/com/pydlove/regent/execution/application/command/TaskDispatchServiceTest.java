package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.service.ExecutionConcurrencyGate;
import com.pydlove.regent.governance.application.command.DecisionRaiseService;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 调度器测试 —— 认领的边界。
 *
 * <p>这里最要紧的两条：认领失败必须让任务<b>原样留在库里</b>（不能出现「状态已改但没人跑」），
 * 以及暂停的项目不许再开始新任务。
 */
class TaskDispatchServiceTest {

    private static final com.pydlove.regent.project.domain.model.ProjectId PROJECT =
            com.pydlove.regent.project.domain.model.ProjectId.of(1L);

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Runs runs = new ExecutionFakes.Runs();
    private final ExecutionFakes.Events events = new ExecutionFakes.Events();
    private final ExecutionFakes.Decisions decisions = new ExecutionFakes.Decisions();
    private final ExecutionFakes.Bugs bugs = new ExecutionFakes.Bugs();
    private final ExecutionFakes.Rules rules = new ExecutionFakes.Rules();
    private final ExecutionFakes.Projects projects = new ExecutionFakes.Projects();
    private final ExecutionFakes.Attachments attachments = new ExecutionFakes.Attachments();
    private final ExecutionFakes.Branches branches = new ExecutionFakes.Branches();
    private final ExecutionFakes.Executor executor = new ExecutionFakes.Executor();
    private final ExecutionFakes.Workers workers = new ExecutionFakes.Workers();
    private final ExecutionFakes.Transactions transactions = new ExecutionFakes.Transactions();
    private final ExecutionConcurrencyGate concurrencyGate = new ExecutionConcurrencyGate(64);

    private TaskDispatchService service;

    @BeforeEach
    void setUp() {
        tasks.wire(runs);
        events.wire(runs);
        projects.put(ExecutionFakes.Projects.profile(1L, DecisionLevel.L1, true));
        DecisionRaiseService decisionRaiseService = new DecisionRaiseService(rules, decisions);
        TaskRunService runService = new TaskRunService(tasks, runs, events, attachments, projects,
                new ExecutionFakes.Environments(),
                new ExecutionFakes.TaskPlugins(),
                new RunSettlementService(tasks, decisions, decisionRaiseService, bugs, taskId -> {
                }),
                executor, branches, Duration.ofMinutes(30));
        service = new TaskDispatchService(
                tasks, projects, branches, new BranchProvisioningService(branches),
                runService, workers, concurrencyGate, transactions);
    }

    private Task add(TaskStatus status) {
        return add(1L, status);
    }

    private Task add(long id, TaskStatus status) {
        Task task = ExecutionFakes.task(id, PROJECT, status);
        tasks.add(task);
        return task;
    }

    @Test
    void 批量派发时单项失败不阻断后续任务() {
        add(1L, TaskStatus.PENDING);
        add(2L, TaskStatus.DONE);
        add(3L, TaskStatus.PENDING);

        TaskDispatchBatchResult result = service.dispatchBatch(List.of(
                TaskId.of(1L), TaskId.of(2L), TaskId.of(3L)));

        assertEquals(List.of(TaskId.of(1L), TaskId.of(3L)), result.dispatchedTaskIds());
        assertEquals(1, result.failures().size());
        assertEquals(TaskId.of(2L), result.failures().get(0).taskId());
        assertEquals(2, workers.size());
        assertEquals(TaskStatus.DONE, tasks.get(2L).getStatus());
    }

    @Test
    void 批量派发时重复任务ID只处理一次() {
        add(TaskStatus.PENDING);

        TaskDispatchBatchResult result = service.dispatchBatch(List.of(
                TaskId.of(1L), TaskId.of(1L)));

        assertEquals(List.of(TaskId.of(1L)), result.dispatchedTaskIds());
        assertTrue(result.failures().isEmpty());
        assertEquals(1, workers.size());
    }

    @Nested
    @DisplayName("手动派发")
    class Manual {

        @Test
        void 派发待执行任务先切分支再认领() {
            add(TaskStatus.PENDING);

            Task claimed = service.dispatch(TaskId.of(1L));

            assertEquals(TaskStatus.RUNNING, claimed.getStatus());
            assertEquals("main", claimed.getBranchName().orElseThrow());
            assertTrue(branches.created.isEmpty());
            assertEquals(1, workers.size(), "执行必须交给工作线程，不能挂在调用线程上");
            assertEquals(TaskStatus.RUNNING, tasks.get(1L).getStatus());
        }

        @Test
        void 非Git目录派发时分支为空() {
            branches.current = null;
            add(TaskStatus.PENDING);

            Task claimed = service.dispatch(TaskId.of(1L));

            assertEquals(TaskStatus.RUNNING, claimed.getStatus());
            assertTrue(claimed.getBranchName().isEmpty());
            assertTrue(branches.switched.isEmpty());
            assertEquals(1, workers.size());
        }

        @Test
        void 事务内派发时等工作线程提交后再启动() {
            transactions.deferAfterCommit = true;
            add(TaskStatus.PENDING);

            service.dispatchAfterCommit(TaskId.of(1L));

            assertEquals(0, workers.size());
            transactions.runAfterCommit();
            assertEquals(1, workers.size());
        }

        @Test
        void 事务回滚时释放执行位且不启动线程() {
            transactions.deferAfterCommit = true;
            concurrencyGate.updateLimit(1);
            add(TaskStatus.PENDING);

            service.dispatchAfterCommit(TaskId.of(1L));
            transactions.runAfterRollback();

            assertEquals(0, concurrencyGate.activeCount());
            assertEquals(0, workers.size());
        }

        @Test
        void 项目暂停时不派发也不切分支() {
            projects.put(ExecutionFakes.Projects.profile(1L, DecisionLevel.L1, false));
            add(TaskStatus.PENDING);

            assertThrows(DomainException.class, () -> service.dispatch(TaskId.of(1L)));

            assertEquals(TaskStatus.PENDING, tasks.get(1L).getStatus());
            assertTrue(branches.created.isEmpty(), "项目都暂停了还去切分支，等于把工作区搅了一遍");
            assertEquals(0, workers.size());
        }

        @Test
        void 分支准备失败时任务原样留在待执行() {
            add(TaskStatus.PENDING);
            branches.failure = new DomainException("工作区不是 Git 仓库");

            assertThrows(DomainException.class, () -> service.dispatch(TaskId.of(1L)));

            assertEquals(TaskStatus.PENDING, tasks.get(1L).getStatus());
            assertEquals(0, workers.size(), "认领失败就不能再提交执行");
        }

        @Test
        void 默认在当前分支派发且不创建审批单() {
            add(TaskStatus.PENDING);

            Task claimed = service.dispatch(TaskId.of(1L));

            assertEquals(TaskStatus.RUNNING, claimed.getStatus());
            assertEquals("main", claimed.getBranchName().orElseThrow());
            assertTrue(branches.created.isEmpty());
            assertTrue(decisions.all().isEmpty());
            assertEquals(1, workers.size());
        }

        @Test
        void 重复派发被拒绝() {
            add(TaskStatus.PENDING);
            service.dispatch(TaskId.of(1L));

            assertThrows(DomainException.class, () -> service.dispatch(TaskId.of(1L)));
            assertEquals(1, workers.size());
        }

        @Test
        void 已完成的任务不能派发() {
            add(TaskStatus.DONE);

            assertThrows(DomainException.class, () -> service.dispatch(TaskId.of(1L)));
        }

        @Test
        void 达到并发上限时任务进入排队() {
            concurrencyGate.updateLimit(1);
            add(1L, TaskStatus.PENDING);
            add(2L, TaskStatus.PENDING);

            service.dispatch(TaskId.of(1L));

            Task queued = service.dispatch(TaskId.of(2L));

            assertEquals(TaskStatus.QUEUED, queued.getStatus());
            assertEquals(TaskStatus.QUEUED, tasks.get(2L).getStatus());
            assertEquals(0, queued.getAttemptCount(), "等待执行位不算一次执行尝试");
            assertTrue(queued.getStartedAt().isEmpty());
            assertEquals(1, workers.size(), "只有取得执行位的任务才能提交给工作线程");
        }

        @Test
        void 排队任务在前的任务收场后自动补位() {
            executor.script(ExecutionFakes.Executor.success("完成"));
            concurrencyGate.updateLimit(1);
            add(1L, TaskStatus.PENDING);
            add(2L, TaskStatus.PENDING);

            service.dispatch(TaskId.of(1L));
            service.dispatch(TaskId.of(2L));
            workers.runAll();

            assertEquals(TaskStatus.DONE, tasks.get(1L).getStatus());
            assertEquals(TaskStatus.RUNNING, tasks.get(2L).getStatus());
            assertEquals(2, workers.size());
        }

        @Test
        void 续跑会把工作区切回原分支并开启新尝试() {
            Task task = add(TaskStatus.RUNNING);
            task.recordThreadId("th-1");
            runs.save(blockedRun());

            Task claimed = service.dispatch(TaskId.of(1L));

            assertEquals(2, claimed.getAttemptCount(), "人工批准之后的续跑是一次新的执行尝试");
            assertEquals(List.of("codex/task-1"), branches.switched);
            assertEquals(1, workers.size());
        }

        private TaskRun blockedRun() {
            TaskRun run = TaskRun.start(TaskId.of(1L), 1, "codex/task-1");
            run.finish(ExecutionFakes.Executor.blocked(DecisionLevel.L2, true));
            return run;
        }
    }

    @Nested
    @DisplayName("定时扫描")
    class Scan {

        @Test
        void 扫描认领待执行任务() {
            add(TaskStatus.PENDING);

            assertEquals(1, service.dispatchCandidates(10));
            assertEquals(TaskStatus.RUNNING, tasks.get(1L).getStatus());
        }

        @Test
        void 扫描认领排队任务() {
            add(TaskStatus.QUEUED);

            assertEquals(1, service.dispatchCandidates(10));
            assertEquals(TaskStatus.RUNNING, tasks.get(1L).getStatus());
        }

        @Test
        void 扫描不领起没有待续跑信号的任务() {
            Task task = add(TaskStatus.RUNNING);
            task.recordThreadId("th-1");
            TaskRun failed = TaskRun.start(TaskId.of(1L), 1, "codex/task-1");
            failed.finish(ExecutionFakes.Executor.failed("跑挂了"));
            runs.save(failed);

            assertEquals(0, service.dispatchCandidates(10), "进程崩了留下的 RUNNING 不该被自动拉起，只能人工重试");
        }

        @Test
        void 认领失败后进入冷却不再反复重试() {
            add(TaskStatus.PENDING);
            branches.failure = new DomainException("分支名冲突");

            assertEquals(0, service.dispatchCandidates(10));
            assertEquals(0, service.dispatchCandidates(10), "同一分钟内不该把同样的失败再试一遍");
            assertEquals(TaskStatus.PENDING, tasks.get(1L).getStatus());
        }

        @Test
        void 扫描不会超过并发上限() {
            concurrencyGate.updateLimit(1);
            add(1L, TaskStatus.PENDING);
            add(2L, TaskStatus.PENDING);

            assertEquals(1, service.dispatchCandidates(10));
            assertEquals(TaskStatus.RUNNING, tasks.get(1L).getStatus());
            assertEquals(TaskStatus.PENDING, tasks.get(2L).getStatus());
        }
    }
}
