package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskAdjustment;
import com.pydlove.regent.execution.domain.model.TaskAdjustmentSource;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskAdjustmentServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);
    private static final UserId OWNER = UserId.of(1L);

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Adjustments adjustments = new ExecutionFakes.Adjustments();
    private final ExecutionFakes.Transactions transactions = new ExecutionFakes.Transactions();
    private final TaskAdjustmentService service =
            new TaskAdjustmentService(tasks, adjustments, transactions);

    @BeforeEach
    void setUp() {
        tasks.add(ExecutionFakes.task(1L, PROJECT, TaskStatus.RUNNING));
    }

    @Test
    @DisplayName("运行中的任务可以临时补充调整")
    void 临时调整() {
        TaskAdjustment adjustment = service.add(new AddTaskAdjustmentCommand(
                TaskId.of(1L), OWNER, null, "补上移动端布局"));

        assertEquals(TaskAdjustmentSource.TEMPORARY, adjustment.getSource());
        assertEquals("补上移动端布局", adjustment.getContent());
        assertEquals(1, adjustments.all().size());
        assertEquals(1, transactions.transactions);
    }

    @Test
    @DisplayName("引用同项目任务时保存标题与指令快照")
    void 引用任务快照() {
        Task source = Task.manual(PROJECT, "登录页调整", "把登录页改成双栏并补错误提示", 0);
        source.assignIdentity(TaskId.of(2L));
        tasks.add(source);

        TaskAdjustment adjustment = service.add(new AddTaskAdjustmentCommand(
                TaskId.of(1L), OWNER, source.getId(), null));

        assertEquals(TaskAdjustmentSource.TASK, adjustment.getSource());
        assertEquals(source.getId(), adjustment.getSourceTaskId().orElseThrow());
        assertEquals("登录页调整", adjustment.getSourceTitle().orElseThrow());
        assertEquals("把登录页改成双栏并补错误提示", adjustment.getContent());
    }

    @Test
    @DisplayName("来源任务必须属于同项目")
    void 拒绝跨项目来源() {
        Task source = Task.manual(ProjectId.of(2L), "别的项目", "别的项目任务", 0);
        source.assignIdentity(TaskId.of(2L));
        tasks.add(source);

        assertThrows(DomainException.class, () -> service.add(new AddTaskAdjustmentCommand(
                TaskId.of(1L), OWNER, source.getId(), null)));
    }

    @Test
    @DisplayName("来源任务与临时文本只能二选一")
    void 二选一() {
        assertThrows(DomainException.class, () -> service.add(new AddTaskAdjustmentCommand(
                TaskId.of(1L), OWNER, null, null)));
        assertThrows(DomainException.class, () -> service.add(new AddTaskAdjustmentCommand(
                TaskId.of(1L), OWNER, TaskId.of(2L), "同时给两个来源")));
    }

    @Test
    @DisplayName("已完成任务不能再接收调整")
    void 完成任务拒绝调整() {
        tasks.add(ExecutionFakes.task(3L, PROJECT, TaskStatus.DONE));

        assertThrows(DomainException.class, () -> service.add(new AddTaskAdjustmentCommand(
                TaskId.of(3L), OWNER, null, "晚到的调整")));
    }
}
