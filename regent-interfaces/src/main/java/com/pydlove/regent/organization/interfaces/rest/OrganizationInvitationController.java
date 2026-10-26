package com.pydlove.regent.organization.interfaces.rest;

import com.pydlove.regent.identity.application.command.AuthService;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.organization.application.command.AcceptOrganizationInvitationCommand;
import com.pydlove.regent.organization.application.command.OrganizationCommandService;
import com.pydlove.regent.organization.application.query.OrganizationQueryService;
import com.pydlove.regent.organization.interfaces.dto.AcceptOrganizationInvitationRequest;
import com.pydlove.regent.organization.interfaces.dto.AcceptedOrganizationInvitationResponse;
import com.pydlove.regent.organization.interfaces.dto.OrganizationInvitationPreviewResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
public class OrganizationInvitationController {

    private final OrganizationQueryService queryService;
    private final OrganizationCommandService commandService;
    private final AuthService authService;

    public OrganizationInvitationController(OrganizationQueryService queryService,
                                            OrganizationCommandService commandService,
                                            AuthService authService) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.authService = authService;
    }

    @GetMapping("/{token}")
    public OrganizationInvitationPreviewResponse preview(@PathVariable String token) {
        return OrganizationInvitationPreviewResponse.from(
                queryService.previewInvitation(token));
    }

    @PostMapping("/{token}/accept")
    public AcceptedOrganizationInvitationResponse accept(
            @PathVariable String token,
            @RequestBody(required = false) AcceptOrganizationInvitationRequest request,
            HttpServletRequest servletRequest) {
        UserId actorId = authService.authenticate(
                        AuthSessionInterceptor.readToken(servletRequest))
                .map(user -> UserId.of(user.id()))
                .orElse(null);
        AcceptOrganizationInvitationCommand command = request == null
                ? new AcceptOrganizationInvitationCommand(null, null)
                : new AcceptOrganizationInvitationCommand(request.displayName(), request.password());
        return AcceptedOrganizationInvitationResponse.from(
                commandService.acceptInvitation(token, command, actorId));
    }
}
