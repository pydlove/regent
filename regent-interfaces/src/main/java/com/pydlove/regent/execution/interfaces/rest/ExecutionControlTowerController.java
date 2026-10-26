package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.query.ExecutionControlTowerService;
import com.pydlove.regent.execution.interfaces.dto.ExecutionControlTowerResponse;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.application.query.ProjectAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 统一执行控制塔读接口。 */
@RestController
@RequestMapping("/api/execution-control-tower")
public class ExecutionControlTowerController {

    private final ExecutionControlTowerService service;
    private final ProjectAccessService projectAccessService;

    public ExecutionControlTowerController(ExecutionControlTowerService service,
                                           ProjectAccessService projectAccessService) {
        this.service = service;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    public ExecutionControlTowerResponse snapshot(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        ProjectId project = projectId == null ? null : ProjectId.of(projectId);
        if (project != null) projectAccessService.require(project, ownerId);
        return ExecutionControlTowerResponse.from(service.snapshot(ownerId, project, limit));
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
