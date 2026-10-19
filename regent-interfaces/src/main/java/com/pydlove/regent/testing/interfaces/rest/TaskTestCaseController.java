package com.pydlove.regent.testing.interfaces.rest;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.application.query.TaskQueryService;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.testing.application.command.TaskTestCaseService;
import com.pydlove.regent.testing.application.query.TestCaseRunQueryService;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.interfaces.dto.BindTaskTestCasesRequest;
import com.pydlove.regent.testing.interfaces.dto.TaskTestCaseResponse;
import com.pydlove.regent.testing.interfaces.dto.TestCaseRunSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 任务测试用例绑定与执行记录接口。 */
@RestController
@RequestMapping("/api/tasks/{taskId}")
public class TaskTestCaseController {

    private final TaskTestCaseService taskTestCaseService;
    private final TestCaseRunQueryService runQueryService;
    private final TaskQueryService taskQueryService;

    public TaskTestCaseController(TaskTestCaseService taskTestCaseService,
                                  TestCaseRunQueryService runQueryService,
                                  TaskQueryService taskQueryService) {
        this.taskTestCaseService = taskTestCaseService;
        this.runQueryService = runQueryService;
        this.taskQueryService = taskQueryService;
    }

    @GetMapping("/test-cases")
    public List<TaskTestCaseResponse> list(@PathVariable long taskId, HttpServletRequest request) {
        taskQueryService.get(TaskId.of(taskId), currentUser(request));
        return taskTestCaseService.list(TaskId.of(taskId)).stream()
                .map(TaskTestCaseResponse::from)
                .toList();
    }

    @PutMapping("/test-cases")
    public List<TaskTestCaseResponse> bind(@PathVariable long taskId,
                                           @RequestBody BindTaskTestCasesRequest request,
                                           HttpServletRequest servletRequest) {
        if (request.testCaseIds() == null) {
            throw new IllegalArgumentException("缺少 testCaseIds");
        }
        taskQueryService.get(TaskId.of(taskId), currentUser(servletRequest));
        return taskTestCaseService.bind(
                        TaskId.of(taskId),
                        request.testCaseIds().stream().map(TestCaseId::of).toList())
                .stream()
                .map(TaskTestCaseResponse::from)
                .toList();
    }

    @GetMapping("/test-case-runs")
    public List<TestCaseRunSummaryResponse> runs(@PathVariable long taskId,
                                                 HttpServletRequest request) {
        taskQueryService.get(TaskId.of(taskId), currentUser(request));
        return runQueryService.listByTask(TaskId.of(taskId)).stream()
                .map(TestCaseRunSummaryResponse::from)
                .toList();
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
