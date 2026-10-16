package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.model.BugStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskSource;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.service.ExecutionConcurrencyGate;
import com.pydlove.regent.governance.application.command.DecisionRaiseService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 缺陷派发修复任务的跨聚合编排。 */
class BugFixDispatchServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);

    private final ExecutionFakes.Bugs bugs = new ExecutionFakes.Bugs();
    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Runs runs = new ExecutionFakes.Runs();
    private final ExecutionFakes.Events events = new ExecutionFakes.Events();
    private final ExecutionFakes.Decisions decisions = new ExecutionFakes.Decisions();
    private final ExecutionFakes.Rules rules = new ExecutionFakes.Rules();
    private final ExecutionFakes.Projects projects = new ExecutionFakes.Projects();
    private final ExecutionFakes.Attachments attachments = new ExecutionFakes.Attachments();
    private final ExecutionFakes.Branches branches = new ExecutionFakes.Branches();
    private final ExecutionFakes.Executor executor = new ExecutionFakes.Executor();
    private final ExecutionFakes.Workers workers = new ExecutionFakes.Workers();
    private final ExecutionFakes.Transactions transactions = new ExecutionFakes.Transactions();

    private BugFixDispatchService service;

    @BeforeEach
    void setUp() {
        tasks.wire(runs);
        events.wire(runs);
        projects.put(ExecutionFakes.Projects.profile(1L, DecisionLevel.L1, true));
        TaskRunService runService = new TaskRunService(tasks, runs, events, attachments, projects,
                new ExecutionFakes.Environments(),
                new RunSettlementService(tasks, decisions, new DecisionRaiseService(rules, decisions), bugs),
                executor, Duration.ofMinutes(30));
        TaskDispatchService dispatchService = new TaskDispatchService(
                tasks,
                projects,
                branches,
                runService,
                workers,
                new ExecutionConcurrencyGate(1),
                transactions);
        service = new BugFixDispatchService(bugs, tasks, dispatchService, transactions);
    }

    @Test
    @DisplayName("创建 BUG_FIX 任务、回填缺陷单并认领执行")
    void 派发修复任务() {
        Bug bug = Bug.reportManually(
                PROJECT,
                "导出文件名乱码",
                BugSeverity.MAJOR,
                "下载含中文名的导出文件",
                "src/export/CsvExporter.java",
                "响应头仍按 ISO-8859-1 编码");
        bugs.save(bug);
        transactions.deferAfterCommit = true;

        Task task = service.dispatchFix(bug.getId());

        assertEquals(TaskSource.BUG_FIX, task.getSource());
        assertEquals(bug.getId(), task.getBugId().orElseThrow());
        assertEquals(TaskStatus.RUNNING, task.getStatus());
        assertTrue(task.getPrompt().contains("缺陷单：#1"));
        assertTrue(task.getPrompt().contains("下载含中文名的导出文件"));
        assertTrue(task.getPrompt().contains("src/export/CsvExporter.java"));

        Bug reloaded = bugs.findById(bug.getId()).orElseThrow();
        assertEquals(BugStatus.FIXING, reloaded.getStatus());
        assertEquals(task.getId(), reloaded.getFixTaskId().orElseThrow());
        assertEquals(1, transactions.transactions);
        assertEquals(0, workers.size());

        transactions.runAfterCommit();
        assertEquals(1, workers.size());
    }

    @Test
    @DisplayName("已有修复任务的缺陷不能重复派发")
    void 重复派发拒绝() {
        Bug bug = Bug.reportManually(PROJECT, "分页错位", BugSeverity.MINOR, null, null, null);
        bugs.save(bug);
        bug.requestFix(TaskId.of(99L));

        assertThrows(DomainException.class, () -> service.dispatchFix(bug.getId()));
        assertEquals(0, workers.size());
    }
}
