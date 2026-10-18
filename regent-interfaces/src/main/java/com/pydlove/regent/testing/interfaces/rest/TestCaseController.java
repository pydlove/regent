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

    public TestCaseController(TestCaseCommandService commandService,
                              TestCaseExecutionService executionService,
                              TestCaseQueryService queryService,
                              TestCaseRunQueryService runQueryService) {
        this.commandService = commandService;
        this.executionService = executionService;
        this.queryService = queryService;
        this.runQueryService = runQueryService;
    }

    @GetMapping("/test-cases")
    public List<TestCaseSummaryResponse> list(@RequestParam(required = false) Long projectId) {
        return queryService.list(projectId).stream()
                .map(TestCaseSummaryResponse::from)
                .toList();
    }

    @GetMapping("/test-cases/{id}")
    public TestCaseDetailResponse detail(@PathVariable long id) {
        return TestCaseDetailResponse.from(queryService.getOverview(TestCaseId.of(id)));
    }

    @PostMapping("/test-cases")
    public TestCaseDetailResponse create(@RequestBody TestCaseSaveRequest request) {
        var created = commandService.create(toCommand(request));
        return TestCaseDetailResponse.from(queryService.getOverview(created.getId()));
    }

    @PutMapping("/test-cases/{id}")
    public TestCaseDetailResponse update(@PathVariable long id,
                                         @RequestBody TestCaseSaveRequest request) {
        var updated = commandService.update(TestCaseId.of(id), toCommand(request));
        return TestCaseDetailResponse.from(queryService.getOverview(updated.getId()));
    }

    @PutMapping("/test-cases/{id}/enabled")
    public TestCaseDetailResponse setEnabled(@PathVariable long id,
                                             @RequestBody TestCaseEnabledRequest request) {
        if (request.enabled() == null) {
            throw new IllegalArgumentException("缺少 enabled");
        }
        var updated = commandService.setEnabled(TestCaseId.of(id), request.enabled());
        return TestCaseDetailResponse.from(queryService.getOverview(updated.getId()));
    }

    @DeleteMapping("/test-cases/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        commandService.delete(TestCaseId.of(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/test-cases/{id}/runs")
    public List<TestCaseRunSummaryResponse> runs(@PathVariable long id,
                                                  @RequestParam(defaultValue = "30") int limit) {
        return runQueryService.listByTestCase(TestCaseId.of(id), limit).stream()
                .map(TestCaseRunSummaryResponse::from)
                .toList();
    }

    @PostMapping("/test-cases/{id}/run")
    public TestCaseRunSummaryResponse run(@PathVariable long id) {
        return TestCaseRunSummaryResponse.from(
                executionService.runNow(TestCaseId.of(id)));
    }

    @PostMapping("/test-cases/run")
    public TestCaseBatchRunResponse runBatch(@RequestBody RunTestCasesRequest request) {
        if (request.testCaseIds() == null) {
            throw new IllegalArgumentException("缺少 testCaseIds");
        }
        return TestCaseBatchRunResponse.from(executionService.runMany(
                request.testCaseIds().stream().map(TestCaseId::of).toList()));
    }

    @GetMapping("/test-case-runs/{id}")
    public TestCaseRunDetailResponse runDetail(@PathVariable long id) {
        return TestCaseRunDetailResponse.from(runQueryService.get(TestCaseRunId.of(id)));
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
