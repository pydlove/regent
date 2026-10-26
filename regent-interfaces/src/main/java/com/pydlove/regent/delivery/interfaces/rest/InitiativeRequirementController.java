package com.pydlove.regent.delivery.interfaces.rest;

import com.pydlove.regent.delivery.application.command.CreateInitiativeRequirementCommand;
import com.pydlove.regent.delivery.application.command.RequirementDeliveryCommandService;
import com.pydlove.regent.delivery.application.query.RequirementDeliveryQueryService;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.interfaces.dto.InitiativeRequirementCreateRequest;
import com.pydlove.regent.delivery.interfaces.dto.RequirementDeliveryResponse;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 立项下的需求录入入口。 */
@RestController
@RequestMapping("/api/initiatives/{initiativeId}/requirements")
public class InitiativeRequirementController {

    private final RequirementDeliveryCommandService commandService;
    private final RequirementDeliveryQueryService queryService;

    public InitiativeRequirementController(RequirementDeliveryCommandService commandService,
                                           RequirementDeliveryQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public RequirementDeliveryResponse create(
            @PathVariable long initiativeId,
            @RequestBody InitiativeRequirementCreateRequest request,
            HttpServletRequest servletRequest) {
        UserId ownerId = currentUser(servletRequest);
        var delivery = commandService.createWithRequirement(
                InitiativeId.of(initiativeId),
                new CreateInitiativeRequirementCommand(
                        request.title(),
                        request.rawText(),
                        request.acceptanceText(),
                        request.clarificationText()),
                ownerId);
        return RequirementDeliveryResponse.from(
                queryService.get(delivery.getId(), ownerId));
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
