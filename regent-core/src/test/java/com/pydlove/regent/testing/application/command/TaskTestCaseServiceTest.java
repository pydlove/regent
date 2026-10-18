package com.pydlove.regent.testing.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.support.ExecutionFakes;
import com.pydlove.regent.support.TestingFakes;
import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskTestCaseServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);

    private ExecutionFakes.Tasks tasks;
    private TestingFakes.TestCases testCases;
    private TestingFakes.Bindings bindings;
    private TaskTestCaseService service;
    private Task task;
    private TestCase testCase;

    @BeforeEach
    void setUp() {
        tasks = new ExecutionFakes.Tasks();
        testCases = new TestingFakes.TestCases();
        bindings = new TestingFakes.Bindings();
        service = new TaskTestCaseService(
                tasks, testCases, bindings, new ExecutionFakes.Transactions());

        task = Task.manual(PROJECT, "实现登录", "实现登录页", 0);
        tasks.save(task);
        testCase = testCases.put(TestCase.create(PROJECT, "登录页回归", TestCaseType.WEB,
                null, "npx playwright test", null, null, true));
    }

    @Test
    @DisplayName("同项目用例可以绑定到待执行任务")
    void bindsAvailableCase() {
        var result = service.bind(task.getId(), List.of(testCase.getId()));

        assertEquals(1, result.size());
        assertEquals(testCase.getId(), result.get(0).testCaseId());
        assertEquals(1, bindings.findByTask(task.getId()).size());
    }

    @Test
    @DisplayName("跨项目测试用例不能绑定")
    void rejectsCrossProjectCase() {
        TestCase other = testCases.put(TestCase.create(ProjectId.of(2L), "别的项目", TestCaseType.API,
                null, "curl http://localhost", null, null, true));

        assertThrows(DomainException.class, () -> service.bind(task.getId(), List.of(other.getId())));
    }

    @Test
    @DisplayName("停用用例不能绑定")
    void rejectsDisabledCase() {
        testCase.changeEnabled(false);

        assertThrows(DomainException.class, () -> service.bind(task.getId(), List.of(testCase.getId())));
    }

    @Test
    @DisplayName("任务执行后不能改绑")
    void rejectsRunningTask() {
        task.dispatch("codex/task-1");
        tasks.save(task);

        assertEquals(TaskStatus.RUNNING, tasks.findById(task.getId()).orElseThrow().getStatus());
        assertThrows(DomainException.class, () -> service.bind(task.getId(), List.of(testCase.getId())));
    }

    @Test
    @DisplayName("空选择会清空已有绑定")
    void clearsBindings() {
        service.bind(task.getId(), List.of(testCase.getId()));

        service.bind(task.getId(), List.of());

        assertEquals(List.of(), bindings.findByTask(task.getId()));
    }
}
