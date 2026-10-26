package com.pydlove.regent.delivery.interfaces.rest;

import com.pydlove.regent.delivery.application.command.ReleaseAction;
import com.pydlove.regent.delivery.application.command.ReleaseCommandService;
import com.pydlove.regent.delivery.application.query.ReleaseQueryService;
import com.pydlove.regent.delivery.domain.model.ReleaseId;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.interfaces.dto.ReleaseActionRequest;
import com.pydlove.regent.delivery.interfaces.dto.ReleaseCreateRequest;
import com.pydlove.regent.delivery.interfaces.dto.ReleaseResponse;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.domain.model.ProjectId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("deliveryReleaseController")
@RequestMapping("/api/releases")
public class ReleaseController {

    private final ReleaseCommandService commandService;
    private final ReleaseQueryService queryService;

    public ReleaseController(ReleaseCommandService commandService,
                             ReleaseQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public List<ReleaseResponse> list(@RequestParam(required = false) Long projectId,
                                      HttpServletRequest request) {
        return queryService.list(
                        projectId == null ? null : ProjectId.of(projectId),
                        currentUser(request))
                .stream().map(ReleaseResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ReleaseResponse detail(@PathVariable long id, HttpServletRequest request) {
        return ReleaseResponse.from(queryService.get(ReleaseId.of(id), currentUser(request)));
    }

    @PostMapping
    public ReleaseResponse create(@RequestBody ReleaseCreateRequest request,
                                  HttpServletRequest servletRequest) {
        if (request.projectId() == null || request.deliveryIds() == null || request.deliveryIds().isEmpty()) {
            throw new IllegalArgumentException("缺少项目或需求交付单");
        }
        var release = commandService.create(
                ProjectId.of(request.projectId()),
                request.version(),
                request.gitCommit(),
                request.digest(),
                request.sizeBytes() == null ? 0 : request.sizeBytes(),
                request.storageRef(),
                request.releaseNotes(),
                request.rollbackPlan(),
                request.deliveryIds().stream().map(RequirementDeliveryId::of).toList(),
                currentUser(servletRequest));
        return ReleaseResponse.from(queryService.get(release.getId(), currentUser(servletRequest)));
    }

    @PostMapping("/{id}/actions")
    public ReleaseResponse act(@PathVariable long id,
                               @RequestBody ReleaseActionRequest request,
                               HttpServletRequest servletRequest) {
        ReleaseAction action;
        try {
            action = ReleaseAction.valueOf(request.action());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("不支持的发布动作：" + request.action());
        }
        var release = commandService.act(
                ReleaseId.of(id), action, request.comment(), currentUser(servletRequest));
        return ReleaseResponse.from(queryService.get(release.getId(), currentUser(servletRequest)));
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
