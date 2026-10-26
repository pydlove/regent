package com.pydlove.regent.delivery.interfaces.rest;

import com.pydlove.regent.delivery.application.command.RequirementDeliveryAction;
import com.pydlove.regent.delivery.application.command.RequirementDeliveryCommandService;
import com.pydlove.regent.delivery.application.query.RequirementDeliveryQueryService;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.interfaces.dto.RequirementDeliveryActionRequest;
import com.pydlove.regent.delivery.interfaces.dto.RequirementDeliveryCreateRequest;
import com.pydlove.regent.delivery.interfaces.dto.RequirementDeliveryResponse;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/requirement-deliveries")
public class RequirementDeliveryController {

    private final RequirementDeliveryCommandService commandService;
    private final RequirementDeliveryQueryService queryService;

    public RequirementDeliveryController(RequirementDeliveryCommandService commandService,
                                         RequirementDeliveryQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public List<RequirementDeliveryResponse> list(
            @RequestParam(required = false) Long projectId,
            HttpServletRequest request) {
        return queryService.list(
                        projectId == null ? null : ProjectId.of(projectId),
                        currentUser(request))
                .stream().map(RequirementDeliveryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public RequirementDeliveryResponse detail(@PathVariable long id, HttpServletRequest request) {
        return RequirementDeliveryResponse.from(
                queryService.get(RequirementDeliveryId.of(id), currentUser(request)));
    }

    @PostMapping
    public RequirementDeliveryResponse create(@RequestBody RequirementDeliveryCreateRequest request,
                                              HttpServletRequest servletRequest) {
        if (request.initiativeId() == null || request.requirementId() == null) {
            throw new IllegalArgumentException("缺少 initiativeId 或 requirementId");
        }
        var delivery = commandService.create(
                InitiativeId.of(request.initiativeId()),
                RequirementId.of(request.requirementId()),
                currentUser(servletRequest));
        return RequirementDeliveryResponse.from(
                queryService.get(delivery.getId(), currentUser(servletRequest)));
    }

    @PostMapping("/{id}/actions")
    public RequirementDeliveryResponse act(@PathVariable long id,
                                           @RequestBody RequirementDeliveryActionRequest request,
                                           HttpServletRequest servletRequest) {
        RequirementDeliveryAction action;
        try {
            action = RequirementDeliveryAction.valueOf(request.action());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("不支持的交付动作：" + request.action());
        }
        var delivery = commandService.act(
                RequirementDeliveryId.of(id),
                action,
                request.comment(),
                request.designSummary(),
                request.designContent(),
                currentUser(servletRequest));
        return RequirementDeliveryResponse.from(
                queryService.get(delivery.getId(), currentUser(servletRequest)));
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
