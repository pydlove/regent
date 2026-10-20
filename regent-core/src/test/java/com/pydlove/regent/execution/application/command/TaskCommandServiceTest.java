package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskSource;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 任务录入用例测试：项目必须存在，录进来的任务必须是「待执行 + MANUAL」，标题从指令首行推导。 */
class TaskCommandServiceTest {

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Projects projects = new ExecutionFakes.Projects();
    private final TaskCommandService service = new TaskCommandService(tasks, projects);

    private Task create(String prompt) {
        projects.put(ExecutionFakes.Projects.profile(3L, DecisionLevel.L1, true));
        return service.createManual(new CreateTaskCommand(ProjectId.of(3L), prompt, 0));
    }

    @Test
    @DisplayName("项目存在时录入成功：待执行、来源 MANUAL、ID 由仓储回填")
    void createsManualTask() {
        projects.put(ExecutionFakes.Projects.profile(3L, DecisionLevel.L1, true));

        Task task = service.createManual(
                new CreateTaskCommand(ProjectId.of(3L), "把任务列表导出成 CSV", 2));

        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(TaskSource.MANUAL, task.getSource());
        assertEquals(2, task.getPriority());
        assertNotNull(task.getId(), "落库之后就该有 ID —— 前端要拿它选中新行");
        assertEquals(1, tasks.saves);
        assertEquals("把任务列表导出成 CSV", tasks.get(task.getId().value()).getTitle());
    }

    @Test
    @DisplayName("标题取指令的首个非空行，空行跳过")
    void derivesTitleFromFirstContentLine() {
        Task task = create("\n\n  给任务列表加导出按钮  \n导出当前筛选结果，补一条单测。");

        assertEquals("给任务列表加导出按钮", task.getTitle());
        assertTrue(task.getPrompt().contains("补一条单测"), "指令要原样保留，标题只是它的投影");
    }

    @Test
    @DisplayName("首行的 Markdown 记号剥掉，但不动正常的开头")
    void stripsMarkdownMarkers() {
        assertEquals("修复登录页跳转", create("## 修复登录页跳转\n点登录后没反应").getTitle());
        assertEquals("修一下构建", create("> - 修一下构建").getTitle());
        assertEquals("调整文案", create("1. 调整文案").getTitle());
        assertEquals("-5 度以下要告警", create("-5 度以下要告警").getTitle(), "没有空格的减号不是列表记号");
        assertEquals("2 个按钮位置不对", create("2 个按钮位置不对").getTitle());
    }

    @Test
    @DisplayName("首行超长时截断并加省略号：列表一行放不下")
    void truncatesLongTitle() {
        String longLine = "把任务列表的导出功能做成可配置的".repeat(6);

        String title = create(longLine).getTitle();

        assertEquals(TaskCommandService.TITLE_MAX + 1, title.length());
        assertTrue(title.endsWith("…"));
    }

    @Test
    @DisplayName("指令为空直接拒绝 —— 没有指令就没有任务")
    void rejectsBlankPrompt() {
        projects.put(ExecutionFakes.Projects.profile(3L, DecisionLevel.L1, true));

        assertThrows(DomainException.class,
                () -> service.createManual(new CreateTaskCommand(ProjectId.of(3L), "   \n  ", 0)));
        assertThrows(DomainException.class,
                () -> service.createManual(new CreateTaskCommand(ProjectId.of(3L), null, 0)));
        assertEquals(0, tasks.saves);
    }

    @Test
    @DisplayName("项目不存在时拒绝：外键会拦，但报错得是人能行动的那句")
    void rejectsUnknownProject() {
        assertThrows(EntityNotFoundException.class, () -> service.createManual(
                new CreateTaskCommand(ProjectId.of(404L), "把任务列表导出成 CSV", 0)));
        assertEquals(0, tasks.saves);
    }

    @Test
    @DisplayName("项目暂停只拦派发、不拦录入：排队等复工是合理需求")
    void allowsCreatingTaskForPausedProject() {
        projects.put(ExecutionFakes.Projects.profile(3L, DecisionLevel.L1, false));

        Task task = service.createManual(new CreateTaskCommand(ProjectId.of(3L), "把任务列表导出成 CSV", 0));

        assertEquals(TaskStatus.PENDING, task.getStatus());
    }

    @Test
    @DisplayName("基于已有会话创建后续任务：复制线程和分支，但不复制执行状态")
    void createsContinuationTask() {
        Task source = ExecutionFakes.task(9L, ProjectId.of(3L), TaskStatus.RUNNING);
        ExecutionFakes.withThread(source, "thread-parent");
        tasks.add(source);

        Task child = service.createContinuation(new CreateTaskContinuationCommand(
                source.getId(), "继续补齐移动端空状态与错误状态", 4));

        assertEquals(TaskStatus.PENDING, child.getStatus());
        assertEquals(source.getId(), child.getContinuedFromTaskId().orElseThrow());
        assertEquals("thread-parent", child.getCodexThreadId().orElseThrow());
        assertEquals(source.getBranchName().orElseThrow(), child.getBranchName().orElseThrow());
        assertEquals(4, child.getPriority());
    }

    @Test
    @DisplayName("来源任务没有 Codex 会话时拒绝继续创建")
    void rejectsContinuationWithoutThread() {
        Task source = ExecutionFakes.task(9L, ProjectId.of(3L), TaskStatus.DONE);
        tasks.add(source);

        assertThrows(DomainException.class, () -> service.createContinuation(
                new CreateTaskContinuationCommand(source.getId(), "继续开发", 0)));
    }

    @Test
    @DisplayName("编辑任务会重推标题并更新指令与优先级")
    void editsTaskContent() {
        Task task = create("原来的指令");

        Task edited = service.edit(new EditTaskCommand(
                task.getId(), "## 新的首行\n新的完整指令", 6));

        assertEquals("新的首行", edited.getTitle());
        assertEquals("## 新的首行\n新的完整指令", edited.getPrompt());
        assertEquals(6, edited.getPriority());
        assertEquals(1, tasks.editableUpdates);
    }

    @Test
    @DisplayName("已完成的任务不能编辑，也不写回仓储")
    void rejectsEditingDoneTask() {
        Task task = create("已经完成的任务");
        task.dispatch("codex/task-1");
        task.complete();

        assertThrows(DomainException.class,
                () -> service.edit(new EditTaskCommand(task.getId(), "新的指令", 1)));

        assertEquals(0, tasks.editableUpdates);
        assertEquals("已经完成的任务", tasks.get(task.getId().value()).getTitle());
    }

    @Test
    @DisplayName("已终止任务可以重新执行并写回仓储")
    void retriesCancelledTask() {
        Task task = create("终止后需要重新执行");
        task.cancel();

        Task retried = service.retry(task.getId());

        assertEquals(TaskStatus.PENDING, retried.getStatus());
        assertEquals(TaskStatus.PENDING, tasks.get(task.getId().value()).getStatus());
    }

    @Test
    @DisplayName("待执行任务可以删除并从仓储移除")
    void deletesPendingTask() {
        Task task = create("删除这个还没跑的任务");

        service.delete(task.getId());

        assertNull(tasks.get(task.getId().value()));
        assertEquals(List.of(task.getId()), tasks.deleted);
    }

    @Test
    @DisplayName("已终止任务可以删除并从仓储移除")
    void deletesCancelledTask() {
        Task task = create("删除已经终止的任务");
        task.cancel();

        service.delete(task.getId());

        assertNull(tasks.get(task.getId().value()));
        assertEquals(List.of(task.getId()), tasks.deleted);
    }

    @Test
    @DisplayName("执行中任务不能删除")
    void rejectsDeletingStartedTask() {
        Task task = create("已经派发的任务");
        task.dispatch("codex/task-1");

        assertThrows(DomainException.class, () -> service.delete(task.getId()));
        assertEquals(0, tasks.deleted.size());
        assertEquals(task, tasks.get(task.getId().value()));
    }

    @Test
    @DisplayName("批量删除去重，待执行项成功，不可删项逐项返回")
    void deletesBatchWithPartialFailure() {
        Task pending = create("待执行任务");
        Task running = create("执行中任务");
        running.dispatch("codex/task-2");
        TaskId missing = TaskId.of(999L);

        TaskDeleteBatchResult result = service.deleteBatch(
                List.of(pending.getId(), running.getId(), pending.getId(), missing));

        assertEquals(List.of(pending.getId()), result.deletedTaskIds());
        assertEquals(List.of(running.getId(), missing),
                result.failures().stream().map(TaskDeleteBatchResult.Failure::taskId).toList());
        assertNull(tasks.get(pending.getId().value()));
        assertEquals(running, tasks.get(running.getId().value()));
    }
}
