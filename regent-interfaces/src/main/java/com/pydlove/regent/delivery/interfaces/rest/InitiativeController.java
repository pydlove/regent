package com.pydlove.regent.delivery.interfaces.rest;

import com.pydlove.regent.delivery.application.command.InitiativeCommandService;
import com.pydlove.regent.delivery.application.command.SaveInitiativeCommand;
import com.pydlove.regent.delivery.application.query.InitiativeQueryService;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.interfaces.dto.InitiativeActionRequest;
import com.pydlove.regent.delivery.interfaces.dto.InitiativeResponse;
import com.pydlove.regent.delivery.interfaces.dto.InitiativeSaveRequest;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.domain.model.ProjectId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/initiatives")
public class InitiativeController {

    private final InitiativeCommandService commandService;
    private final InitiativeQueryService queryService;

    public InitiativeController(InitiativeCommandService commandService,
                                InitiativeQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public List<InitiativeResponse> list(@RequestParam(required = false) Long projectId,
                                         HttpServletRequest request) {
        return queryService.list(
                        projectId == null ? null : ProjectId.of(projectId),
                        currentUser(request))
                .stream().map(InitiativeResponse::from).toList();
    }

    @GetMapping("/{id}")
    public InitiativeResponse detail(@PathVariable long id, HttpServletRequest request) {
        return InitiativeResponse.from(queryService.get(InitiativeId.of(id), currentUser(request)));
    }

    @PostMapping
    public InitiativeResponse create(@RequestBody InitiativeSaveRequest request,
                                     HttpServletRequest servletRequest) {
        if (request.projectId() == null) throw new IllegalArgumentException("缺少 projectId");
        var initiative = commandService.create(toCommand(request), currentUser(servletRequest));
        return InitiativeResponse.from(queryService.get(initiative.getId(), currentUser(servletRequest)));
    }

    @PutMapping("/{id}")
    public InitiativeResponse update(@PathVariable long id,
                                     @RequestBody InitiativeSaveRequest request,
                                     HttpServletRequest servletRequest) {
        if (request.projectId() == null) throw new IllegalArgumentException("缺少 projectId");
        var initiative = commandService.update(
                InitiativeId.of(id), toCommand(request), currentUser(servletRequest));
        return InitiativeResponse.from(queryService.get(initiative.getId(), currentUser(servletRequest)));
    }

    @PostMapping("/{id}/submit")
    public InitiativeResponse submit(@PathVariable long id, HttpServletRequest request) {
        var initiative = commandService.submit(InitiativeId.of(id), currentUser(request));
        return InitiativeResponse.from(queryService.get(initiative.getId(), currentUser(request)));
    }

    @PostMapping("/{id}/approve")
    public InitiativeResponse approve(@PathVariable long id,
                                      @RequestBody(required = false) InitiativeActionRequest body,
                                      HttpServletRequest request) {
        var initiative = commandService.approve(
                InitiativeId.of(id),
                currentUser(request),
                body == null ? null : body.comment());
        return InitiativeResponse.from(queryService.get(initiative.getId(), currentUser(request)));
    }

    @PostMapping("/{id}/reject")
    public InitiativeResponse reject(@PathVariable long id,
                                     @RequestBody(required = false) InitiativeActionRequest body,
                                     HttpServletRequest request) {
        var initiative = commandService.reject(
                InitiativeId.of(id),
                currentUser(request),
                body == null ? null : body.comment());
        return InitiativeResponse.from(queryService.get(initiative.getId(), currentUser(request)));
    }

    private static SaveInitiativeCommand toCommand(InitiativeSaveRequest request) {
        return new SaveInitiativeCommand(
                ProjectId.of(request.projectId()),
                request.versionId() == null
                        ? null
                        : ProductVersionId.of(request.versionId()),
                request.title(),
                request.businessGoal(),
                request.scopeText(),
                request.outOfScopeText(),
                request.successMetrics(),
                request.stakeholders(),
                request.targetVersion(),
                request.riskText(),
                request.meetingMinutes());
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
