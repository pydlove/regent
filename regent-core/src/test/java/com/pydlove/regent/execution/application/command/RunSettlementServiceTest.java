package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.model.BugStatus;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.governance.application.command.DecisionRaiseService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 修复任务结算与缺陷状态之间的跨聚合编排。 */
class RunSettlementServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Decisions decisions = new ExecutionFakes.Decisions();
    private final ExecutionFakes.Rules rules = new ExecutionFakes.Rules();
    private final ExecutionFakes.Bugs bugs = new ExecutionFakes.Bugs();
    private final List<Long> scheduledTests = new ArrayList<>();
    private final RunSettlementService settlement = new RunSettlementService(
            tasks, decisions, new DecisionRaiseService(rules, decisions), bugs,
            taskId -> scheduledTests.add(taskId.value()));

    private Task task;
    private Bug bug;

    @BeforeEach
    void setUp() {
        scheduledTests.clear();
        bug = Bug.reportManually(PROJECT, "导出文件名乱码", BugSeverity.MAJOR,
                null, null, null);
        bugs.save(bug);

        task = Task.fromBug(PROJECT, bug.getId(), bug.getTitle(), "修复缺陷", 0);
        tasks.save(task);
        bug.requestFix(task.getId());
        bugs.save(bug);
        task.dispatch("codex/task-" + task.getId().value());
        tasks.save(task);
    }

    @Test
    @DisplayName("修复任务成功：任务完成，缺陷进入待验证")
    void 修复任务成功后缺陷待验证() {
        settlement.finishTask(task.getId(), RunStatus.SUCCESS);

        assertEquals(TaskStatus.DONE, tasks.findById(task.getId()).orElseThrow().getStatus());
        assertEquals(List.of(task.getId().value()), scheduledTests,
                "任务成功后应调度绑定的测试用例");
        Bug reloaded = bugs.findById(bug.getId()).orElseThrow();
        assertEquals(BugStatus.FIXED, reloaded.getStatus());
        assertNotNull(reloaded.getFixedAt().orElse(null));
    }

    @Test
    @DisplayName("修复任务失败：缺陷保持修复中，不能替人宣布已修复")
    void 修复任务失败后缺陷保持修复中() {
        settlement.finishTask(task.getId(), RunStatus.FAILED);

        assertEquals(TaskStatus.FAILED, tasks.findById(task.getId()).orElseThrow().getStatus());
        assertEquals(List.of(), scheduledTests, "任务失败不应自动跑测试");
        assertEquals(BugStatus.FIXING, bugs.findById(bug.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("人工已判定不修复时，晚到的执行成功不能覆盖结论")
    void 不修复结论不被执行结果覆盖() {
        bug.wontFix("产品决定保留当前导出行为");
        bugs.save(bug);

        settlement.finishTask(task.getId(), RunStatus.SUCCESS);

        assertEquals(BugStatus.WONTFIX, bugs.findById(bug.getId()).orElseThrow().getStatus());
    }
}
