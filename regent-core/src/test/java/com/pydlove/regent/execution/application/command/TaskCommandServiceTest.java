package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskSource;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
