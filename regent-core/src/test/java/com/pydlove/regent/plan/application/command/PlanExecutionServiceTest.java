package com.pydlove.regent.plan.application.command;

import com.pydlove.regent.execution.application.command.TaskDispatchService;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.plan.domain.model.Plan;
import com.pydlove.regent.plan.domain.model.PlanRunStatus;
import com.pydlove.regent.plan.domain.model.PlanTaskStatus;
import com.pydlove.regent.plan.domain.model.PlanTrigger;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.support.ExecutionFakes;
import com.pydlove.regent.support.PlanFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanExecutionServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1);
    private static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Runs taskRuns = new ExecutionFakes.Runs();
    private final PlanFakes.Plans plans = new PlanFakes.Plans();
    private final PlanFakes.Runs planRuns = new PlanFakes.Runs();
    private final ExecutionFakes.Transactions transactions = new ExecutionFakes.Transactions();
    private final RecordingDispatch dispatch = new RecordingDispatch();
    private PlanExecutionService service;

    @BeforeEach
    void setUp() {
        service = new PlanExecutionService(
                plans, planRuns, tasks, taskRuns, dispatch, transactions,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("人工执行会冻结任务并逐项派发")
    void 人工执行会冻结任务并逐项派发() {
        addTask(11, TaskStatus.PENDING);
        addTask(12, TaskStatus.PENDING);
        Plan plan = savePlan(NOW.plusSeconds(3600), TaskId.of(11), TaskId.of(12));

        var run = service.runNow(plan.getId());

        assertEquals(PlanTrigger.MANUAL, run.getTrigger());
        assertEquals(List.of(TaskId.of(11), TaskId.of(12)), dispatch.dispatched);
        assertEquals(PlanTaskStatus.RUNNING, run.getItems().get(0).getStatus());
        assertEquals(PlanRunStatus.RUNNING, run.getStatus());
    }

    @Test
    @DisplayName("只有到点计划会被定时启动")
    void 只有到点计划会被定时启动() {
        addTask(11, TaskStatus.PENDING);
        addTask(12, TaskStatus.PENDING);
        savePlan(NOW.minusSeconds(1), TaskId.of(11));
        savePlan(NOW.plusSeconds(1), TaskId.of(12));

        assertEquals(1, service.startDuePlans(10));
        assertEquals(List.of(TaskId.of(11)), dispatch.dispatched);
    }

    @Test
    @DisplayName("同一个计划只能被启动一次")
    void 同一个计划只能被启动一次() {
        addTask(11, TaskStatus.PENDING);
        Plan plan = savePlan(NOW.plusSeconds(3600), TaskId.of(11));

        service.runNow(plan.getId());

        assertThrows(DomainException.class, () -> service.runNow(plan.getId()));
        assertEquals(1, dispatch.dispatched.size());
    }

    @Test
    @DisplayName("派发失败记为跳过并可在刷新后完成总结")
    void 派发失败记为跳过并生成失败总结() {
        addTask(11, TaskStatus.DONE);
        Plan plan = savePlan(NOW.plusSeconds(3600), TaskId.of(11));
        dispatch.failing.add(TaskId.of(11));

        var run = service.runNow(plan.getId());
        assertEquals(1, service.refreshRunningRuns(10));

        var saved = planRuns.get(run.getId().value());
        assertEquals(PlanRunStatus.FAILED, saved.getStatus());
        assertTrue(saved.getSummary().contains("跳过 1"));
        assertEquals(com.pydlove.regent.plan.domain.model.PlanStatus.COMPLETED,
                plans.get(plan.getId().value()).getStatus());
    }

    @Test
    @DisplayName("任务等待决策时计划保持运行，终止后才收敛")
    void 任务等待决策时计划保持运行() {
        Task task = addTask(11, TaskStatus.BLOCKED);
        Plan plan = savePlan(NOW.plusSeconds(3600), TaskId.of(11));
        var run = service.runNow(plan.getId());

        assertEquals(0, service.refreshRunningRuns(10));
        assertEquals(PlanRunStatus.RUNNING, planRuns.get(run.getId().value()).getStatus());

        task.cancel();
        assertEquals(1, service.refreshRunningRuns(10));
        assertEquals(PlanRunStatus.FAILED, planRuns.get(run.getId().value()).getStatus());
    }

    private Task addTask(long id, TaskStatus status) {
        Task task = ExecutionFakes.task(id, PROJECT, status);
        tasks.add(task);
        return task;
    }

    private Plan savePlan(Instant scheduledAt, TaskId... taskIds) {
        Plan plan = Plan.schedule("测试计划", List.of(taskIds), scheduledAt);
        plans.save(plan);
        return plan;
    }

    private static final class RecordingDispatch extends TaskDispatchService {

        private final List<TaskId> dispatched = new ArrayList<>();
        private final List<TaskId> failing = new ArrayList<>();

        private RecordingDispatch() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public Task dispatchAfterCommit(TaskId taskId) {
            if (failing.contains(taskId)) {
                throw new DomainException("任务不可派发：" + taskId);
            }
            dispatched.add(taskId);
            return null;
        }
    }
}
