package com.pydlove.regent.bug.interfaces.rest;

import com.pydlove.regent.bug.application.command.BugCommandService;
import com.pydlove.regent.bug.application.command.CreateBugCommand;
import com.pydlove.regent.bug.application.query.BugQueryService;
import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.repository.BugPage;
import com.pydlove.regent.bug.domain.repository.BugQuery;
import com.pydlove.regent.bug.interfaces.dto.BugCreateRequest;
import com.pydlove.regent.bug.interfaces.dto.BugResponse;
import com.pydlove.regent.bug.interfaces.dto.BugWontFixRequest;
import com.pydlove.regent.execution.application.command.BugFixDispatchService;
import com.pydlove.regent.interfaces.dto.PageResponse;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 缺陷单接口。 */
@RestController
@RequestMapping("/api/bugs")
public class BugController {

    private final BugQueryService bugQueryService;
    private final BugCommandService bugCommandService;
    private final BugFixDispatchService bugFixDispatchService;

    public BugController(BugQueryService bugQueryService,
                         BugCommandService bugCommandService,
                         BugFixDispatchService bugFixDispatchService) {
        this.bugQueryService = bugQueryService;
        this.bugCommandService = bugCommandService;
        this.bugFixDispatchService = bugFixDispatchService;
    }

    @GetMapping
    public PageResponse<BugResponse> list(@RequestParam(required = false) String severity,
                                          @RequestParam(required = false) String scope,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        BugPage result = bugQueryService.list(BugQuery.of(severity, scope, keyword, page, size));
        return PageResponse.of(
                result.items().stream().map(BugResponse::from).toList(),
                result.total(), result.page(), result.size());
    }

    @GetMapping("/{id}")
    public BugResponse detail(@PathVariable long id) {
        return BugResponse.from(bugQueryService.get(id));
    }

    @PostMapping
    public BugResponse create(@RequestBody BugCreateRequest request) {
        if (request.projectId() == null) {
            throw new IllegalArgumentException("缺少 projectId：缺陷单必须挂在项目下");
        }
        Bug bug = bugCommandService.createManual(new CreateBugCommand(
                ProjectId.of(request.projectId()),
                request.title(),
                BugSeverity.parse(request.severity()),
                request.reproduction(),
                request.filePath(),
                request.detail()));
        return BugResponse.from(bugQueryService.get(bug.getId().value()));
    }

    /** 创建并派发修复任务，随后返回已回填修复任务号的缺陷单。 */
    @PostMapping("/{id}/fix-task")
    public BugResponse dispatchFixTask(@PathVariable long id) {
        bugFixDispatchService.dispatchFix(BugId.of(id));
        return BugResponse.from(bugQueryService.get(id));
    }

    /** 修复完成，进入待验证；此时缺陷仍未关闭。 */
    @PostMapping("/{id}/mark-fixed")
    public BugResponse markFixed(@PathVariable long id) {
        bugCommandService.markFixed(BugId.of(id));
        return BugResponse.from(bugQueryService.get(id));
    }

    /** 人工验证通过后关闭缺陷。 */
    @PostMapping("/{id}/verify")
    public BugResponse verify(@PathVariable long id) {
        bugCommandService.verify(BugId.of(id));
        return BugResponse.from(bugQueryService.get(id));
    }

    /** 带理由判定不修复，缺陷进入终态。 */
    @PostMapping("/{id}/wont-fix")
    public BugResponse wontFix(@PathVariable long id, @RequestBody BugWontFixRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("缺少不修复理由");
        }
        bugCommandService.wontFix(BugId.of(id), request.reason());
        return BugResponse.from(bugQueryService.get(id));
    }
}
