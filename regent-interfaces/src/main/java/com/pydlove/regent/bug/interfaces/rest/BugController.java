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
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.interfaces.dto.PageResponse;
import com.pydlove.regent.project.application.query.ProjectAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ProjectAccessService projectAccessService;

    public BugController(BugQueryService bugQueryService,
                         BugCommandService bugCommandService,
                         BugFixDispatchService bugFixDispatchService,
                         ProjectAccessService projectAccessService) {
        this.bugQueryService = bugQueryService;
        this.bugCommandService = bugCommandService;
        this.bugFixDispatchService = bugFixDispatchService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    public PageResponse<BugResponse> list(@RequestParam(required = false) String severity,
                                          @RequestParam(required = false) String scope,
                                          @RequestParam(required = false) Long projectId,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          HttpServletRequest request) {
        BugPage result = bugQueryService.list(
                BugQuery.of(severity, scope, projectId, keyword, page, size, currentUser(request)));
        return PageResponse.of(
                result.items().stream().map(BugResponse::from).toList(),
                result.total(), result.page(), result.size());
    }

    @GetMapping("/{id}")
    public BugResponse detail(@PathVariable long id, HttpServletRequest request) {
        return BugResponse.from(bugQueryService.get(id, currentUser(request)));
    }

    @PostMapping
    public BugResponse create(@RequestBody BugCreateRequest request,
                              HttpServletRequest servletRequest) {
        if (request.projectId() == null) {
            throw new IllegalArgumentException("缺少 projectId：缺陷单必须挂在项目下");
        }
        UserId ownerId = currentUser(servletRequest);
        projectAccessService.require(ProjectId.of(request.projectId()), ownerId);
        Bug bug = bugCommandService.createManual(new CreateBugCommand(
                ProjectId.of(request.projectId()),
                request.title(),
                BugSeverity.parse(request.severity()),
                request.reproduction(),
                request.filePath(),
                request.detail()));
        return BugResponse.from(bugQueryService.get(bug.getId().value(), ownerId));
    }

    /** 创建并派发修复任务，随后返回已回填修复任务号的缺陷单。 */
    @PostMapping("/{id}/fix-task")
    public BugResponse dispatchFixTask(@PathVariable long id, HttpServletRequest request) {
        bugQueryService.get(id, currentUser(request));
        bugFixDispatchService.dispatchFix(BugId.of(id));
        return BugResponse.from(bugQueryService.get(id, currentUser(request)));
    }

    /** 修复完成，进入待验证；此时缺陷仍未关闭。 */
    @PostMapping("/{id}/mark-fixed")
    public BugResponse markFixed(@PathVariable long id, HttpServletRequest request) {
        bugQueryService.get(id, currentUser(request));
        bugCommandService.markFixed(BugId.of(id));
        return BugResponse.from(bugQueryService.get(id, currentUser(request)));
    }

    /** 人工验证通过后关闭缺陷。 */
    @PostMapping("/{id}/verify")
    public BugResponse verify(@PathVariable long id, HttpServletRequest request) {
        bugQueryService.get(id, currentUser(request));
        bugCommandService.verify(BugId.of(id));
        return BugResponse.from(bugQueryService.get(id, currentUser(request)));
    }

    /** 判定不修复，缺陷进入终态；理由可选。 */
    @PostMapping("/{id}/wont-fix")
    public BugResponse wontFix(@PathVariable long id, @RequestBody BugWontFixRequest request,
                               HttpServletRequest servletRequest) {
        if (request == null) {
            throw new IllegalArgumentException("缺少请求体");
        }
        bugQueryService.get(id, currentUser(servletRequest));
        bugCommandService.wontFix(BugId.of(id), request.reason());
        return BugResponse.from(bugQueryService.get(id, currentUser(servletRequest)));
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
