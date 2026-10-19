package com.pydlove.regent.testing.interfaces.rest;

import com.pydlove.regent.testing.application.command.SaveTestCaseCommand;
import com.pydlove.regent.testing.application.command.TestCaseCommandService;
import com.pydlove.regent.testing.application.command.TestCaseExecutionService;
import com.pydlove.regent.testing.application.query.TestCaseQueryService;
import com.pydlove.regent.testing.application.query.TestCaseRunQueryService;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseRunId;
import com.pydlove.regent.testing.interfaces.dto.TestCaseDetailResponse;
import com.pydlove.regent.testing.interfaces.dto.TestCaseBatchRunResponse;
import com.pydlove.regent.testing.interfaces.dto.TestCaseEnabledRequest;
import com.pydlove.regent.testing.interfaces.dto.TestCaseRunDetailResponse;
import com.pydlove.regent.testing.interfaces.dto.TestCaseRunSummaryResponse;
import com.pydlove.regent.testing.interfaces.dto.TestCaseSaveRequest;
import com.pydlove.regent.testing.interfaces.dto.TestCaseSummaryResponse;
import com.pydlove.regent.testing.interfaces.dto.RunTestCasesRequest;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.application.query.ProjectAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 测试用例管理与执行记录接口。 */
@RestController
@RequestMapping("/api")
public class TestCaseController {

    private final TestCaseCommandService commandService;
    private final TestCaseExecutionService executionService;
    private final TestCaseQueryService queryService;
    private final TestCaseRunQueryService runQueryService;
    private final ProjectAccessService projectAccessService;

    public TestCaseController(TestCaseCommandService commandService,
                              TestCaseExecutionService executionService,
                              TestCaseQueryService queryService,
                              TestCaseRunQueryService runQueryService,
                              ProjectAccessService projectAccessService) {
        this.commandService = commandService;
        this.executionService = executionService;
        this.queryService = queryService;
        this.runQueryService = runQueryService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping("/test-cases")
    public List<TestCaseSummaryResponse> list(@RequestParam(required = false) Long projectId,
                                              HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        if (projectId != null) {
            projectAccessService.require(ProjectId.of(projectId), ownerId);
        }
        return queryService.list(projectId, ownerId).stream()
                .map(TestCaseSummaryResponse::from)
                .toList();
    }

    @GetMapping("/test-cases/{id}")
    public TestCaseDetailResponse detail(@PathVariable long id, HttpServletRequest request) {
        return TestCaseDetailResponse.from(
                queryService.getOverview(TestCaseId.of(id), currentUser(request)));
    }

    @PostMapping("/test-cases")
    public TestCaseDetailResponse create(@RequestBody TestCaseSaveRequest request,
                                         HttpServletRequest servletRequest) {
        UserId ownerId = currentUser(servletRequest);
        requireProject(request.projectId(), ownerId);
        var created = commandService.create(toCommand(request));
        return TestCaseDetailResponse.from(queryService.getOverview(created.getId(), ownerId));
    }

    @PutMapping("/test-cases/{id}")
    public TestCaseDetailResponse update(@PathVariable long id,
                                         @RequestBody TestCaseSaveRequest request,
                                         HttpServletRequest servletRequest) {
        UserId ownerId = currentUser(servletRequest);
        queryService.get(TestCaseId.of(id), ownerId);
        requireProject(request.projectId(), ownerId);
        var updated = commandService.update(TestCaseId.of(id), toCommand(request));
        return TestCaseDetailResponse.from(queryService.getOverview(updated.getId(), ownerId));
    }

    @PutMapping("/test-cases/{id}/enabled")
    public TestCaseDetailResponse setEnabled(@PathVariable long id,
                                             @RequestBody TestCaseEnabledRequest request,
                                             HttpServletRequest servletRequest) {
        if (request.enabled() == null) {
            throw new IllegalArgumentException("缺少 enabled");
        }
        UserId ownerId = currentUser(servletRequest);
        queryService.get(TestCaseId.of(id), ownerId);
        var updated = commandService.setEnabled(TestCaseId.of(id), request.enabled());
        return TestCaseDetailResponse.from(queryService.getOverview(updated.getId(), ownerId));
    }

    @DeleteMapping("/test-cases/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
        queryService.get(TestCaseId.of(id), currentUser(request));
        commandService.delete(TestCaseId.of(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/test-cases/{id}/runs")
    public List<TestCaseRunSummaryResponse> runs(@PathVariable long id,
                                                  @RequestParam(defaultValue = "30") int limit,
                                                  HttpServletRequest request) {
        queryService.get(TestCaseId.of(id), currentUser(request));
        return runQueryService.listByTestCase(TestCaseId.of(id), limit).stream()
                .map(TestCaseRunSummaryResponse::from)
                .toList();
    }

    @PostMapping("/test-cases/{id}/run")
    public TestCaseRunSummaryResponse run(@PathVariable long id, HttpServletRequest request) {
        queryService.get(TestCaseId.of(id), currentUser(request));
        return TestCaseRunSummaryResponse.from(
                executionService.runNow(TestCaseId.of(id)));
    }

    @PostMapping("/test-cases/run")
    public TestCaseBatchRunResponse runBatch(@RequestBody RunTestCasesRequest request,
                                             HttpServletRequest servletRequest) {
        if (request.testCaseIds() == null) {
            throw new IllegalArgumentException("缺少 testCaseIds");
        }
        UserId ownerId = currentUser(servletRequest);
        request.testCaseIds().forEach(id -> queryService.get(TestCaseId.of(id), ownerId));
        return TestCaseBatchRunResponse.from(executionService.runMany(
                request.testCaseIds().stream().map(TestCaseId::of).toList()));
    }

    @GetMapping("/test-case-runs/{id}")
    public TestCaseRunDetailResponse runDetail(@PathVariable long id, HttpServletRequest request) {
        return TestCaseRunDetailResponse.from(
                runQueryService.get(TestCaseRunId.of(id), currentUser(request)));
    }

    private void requireProject(Long projectId, UserId ownerId) {
        if (projectId == null) {
            throw new IllegalArgumentException("缺少 projectId");
        }
        projectAccessService.require(ProjectId.of(projectId), ownerId);
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }

    private static SaveTestCaseCommand toCommand(TestCaseSaveRequest request) {
        return new SaveTestCaseCommand(
                request.projectId(),
                request.name(),
                request.type(),
                request.description(),
                request.command(),
                request.scriptFilename(),
                request.scriptContent(),
                request.enabled());
    }
}
