package com.pydlove.regent.requirement.interfaces.rest;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.project.application.query.ProjectAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.application.command.RequirementCommandService;
import com.pydlove.regent.requirement.application.command.RequirementIdeaService;
import com.pydlove.regent.requirement.application.command.SaveRequirementCommand;
import com.pydlove.regent.requirement.application.query.RequirementQueryService;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.requirement.domain.model.RequirementTaskDraft;
import com.pydlove.regent.requirement.domain.model.RequirementSource;
import com.pydlove.regent.requirement.interfaces.dto.RequirementResponse;
import com.pydlove.regent.requirement.interfaces.dto.RequirementIdeaGenerateRequest;
import com.pydlove.regent.requirement.interfaces.dto.RequirementSaveRequest;
import com.pydlove.regent.requirement.interfaces.dto.RequirementTaskDraftRequest;
import com.pydlove.regent.requirement.interfaces.dto.RequirementReviewRequest;
import com.pydlove.regent.requirement.interfaces.dto.RequirementTrashResponse;
import com.pydlove.regent.requirement.interfaces.dto.RequirementVersionAssignRequest;
import jakarta.servlet.http.HttpServletRequest;
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

/** 需求澄清、拆解与确认接口。 */
@RestController
@RequestMapping("/api/requirements")
public class RequirementController {

    private final RequirementCommandService commandService;
    private final RequirementIdeaService ideaService;
    private final RequirementQueryService queryService;
    private final ProjectAccessService projectAccessService;

    public RequirementController(RequirementCommandService commandService,
                                 RequirementIdeaService ideaService,
                                 RequirementQueryService queryService,
                                 ProjectAccessService projectAccessService) {
        this.commandService = commandService;
        this.ideaService = ideaService;
        this.queryService = queryService;
        this.projectAccessService = projectAccessService;
    }

    @PostMapping("/ai-generate")
    public List<RequirementResponse> generate(@RequestBody RequirementIdeaGenerateRequest body,
                                              HttpServletRequest request) {
        if (body.projectId() == null || body.initiativeId() == null) {
            throw new IllegalArgumentException("缺少 projectId 或 initiativeId");
        }
        UserId ownerId = currentUser(request);
        ProjectId projectId = ProjectId.of(body.projectId());
        projectAccessService.require(projectId, ownerId);
        return ideaService.generate(
                        projectId,
                        InitiativeId.of(body.initiativeId()),
                        body.idea(),
                        ownerId)
                .stream()
                .map(requirement -> RequirementResponse.from(
                        queryService.get(requirement.getId(), ownerId)))
                .toList();
    }

    @GetMapping
    public List<RequirementResponse> list(@RequestParam(required = false) Long projectId,
                                           HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        ProjectId project = projectId == null ? null : ProjectId.of(projectId);
        if (project != null) projectAccessService.require(project, ownerId);
        return queryService.list(project, ownerId).stream()
                .map(RequirementResponse::from)
                .toList();
    }

    @GetMapping("/trash")
    public List<RequirementTrashResponse> trash(@RequestParam Long projectId,
                                                HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        ProjectId project = ProjectId.of(projectId);
        projectAccessService.require(project, ownerId);
        return queryService.trash(project, ownerId).stream()
                .map(RequirementTrashResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public RequirementResponse detail(@PathVariable long id, HttpServletRequest request) {
        return RequirementResponse.from(
                queryService.get(RequirementId.of(id), currentUser(request)));
    }

    @PostMapping
    public RequirementResponse create(@RequestBody RequirementSaveRequest request,
                                       HttpServletRequest servletRequest) {
        if (request.projectId() == null) {
            throw new IllegalArgumentException("缺少 projectId");
        }
        UserId ownerId = currentUser(servletRequest);
        ProjectId projectId = ProjectId.of(request.projectId());
        projectAccessService.require(projectId, ownerId);
        var requirement = commandService.create(toCommand(request, projectId), ownerId);
        return RequirementResponse.from(queryService.get(requirement.getId(), ownerId));
    }

    @DeleteMapping("/{id}")
    public void moveToTrash(@PathVariable long id, HttpServletRequest request) {
        commandService.moveToTrash(RequirementId.of(id), currentUser(request));
    }

    @PostMapping("/{id}/restore")
    public RequirementResponse restore(@PathVariable long id, HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        var requirement = commandService.restoreFromTrash(RequirementId.of(id), ownerId);
        return RequirementResponse.from(queryService.get(requirement.getId(), ownerId));
    }

    @PutMapping("/{id}")
    public RequirementResponse update(@PathVariable long id,
                                      @RequestBody RequirementSaveRequest request,
                                      HttpServletRequest servletRequest) {
        if (request.projectId() == null) {
            throw new IllegalArgumentException("缺少 projectId");
        }
        UserId ownerId = currentUser(servletRequest);
        ProjectId projectId = ProjectId.of(request.projectId());
        projectAccessService.require(projectId, ownerId);
        var requirement = commandService.update(
                RequirementId.of(id), toCommand(request, projectId), ownerId);
        return RequirementResponse.from(queryService.get(requirement.getId(), ownerId));
    }

    @PostMapping("/{id}/analyze")
    public RequirementResponse analyze(@PathVariable long id, HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        var requirement = commandService.analyze(RequirementId.of(id), ownerId);
        return RequirementResponse.from(queryService.get(requirement.getId(), ownerId));
    }

    @PostMapping("/{id}/confirm")
    public RequirementResponse confirm(@PathVariable long id,
                                       @RequestBody(required = false) List<RequirementTaskDraftRequest> tasks,
                                       HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        List<RequirementTaskDraft> drafts = tasks == null || tasks.isEmpty()
                ? List.of()
                : tasks.stream().map(RequirementTaskDraftRequest::toDomain).toList();
        var requirement = commandService.confirm(RequirementId.of(id), drafts, ownerId);
        return RequirementResponse.from(queryService.get(requirement.getId(), ownerId));
    }

    @PostMapping("/{id}/cancel")
    public RequirementResponse cancel(@PathVariable long id, HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        var requirement = commandService.cancel(RequirementId.of(id), ownerId);
        return RequirementResponse.from(queryService.get(requirement.getId(), ownerId));
    }

    @PutMapping("/{id}/version")
    public RequirementResponse assignVersion(@PathVariable long id,
                                             @RequestBody RequirementVersionAssignRequest body,
                                             HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        var requirement = body.versionId() == null
                ? commandService.removeVersion(RequirementId.of(id), ownerId)
                : commandService.assignVersion(
                        RequirementId.of(id), ProductVersionId.of(body.versionId()), ownerId);
        return RequirementResponse.from(queryService.get(requirement.getId(), ownerId));
    }

    @PostMapping("/{id}/review")
    public RequirementResponse review(@PathVariable long id,
                                      @RequestBody RequirementReviewRequest body,
                                      HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        String action = body.action() == null ? "" : body.action().trim().toUpperCase();
        var requirement = switch (action) {
            case "SUBMIT" -> commandService.submitPoolReview(RequirementId.of(id), ownerId);
            case "APPROVE" -> commandService.approvePoolReview(
                    RequirementId.of(id), ownerId, body.comment());
            case "REJECT" -> commandService.rejectPoolReview(
                    RequirementId.of(id), ownerId, body.comment());
            default -> throw new IllegalArgumentException("不支持的需求评审动作：" + body.action());
        };
        return RequirementResponse.from(queryService.get(requirement.getId(), ownerId));
    }

    private static SaveRequirementCommand toCommand(RequirementSaveRequest request,
                                                    ProjectId projectId) {
        return new SaveRequirementCommand(
                projectId,
                request.initiativeId() == null ? null : InitiativeId.of(request.initiativeId()),
                parseSource(request.source()),
                request.title(),
                request.rawText(),
                request.acceptanceText(),
                request.clarificationText());
    }

    private static RequirementSource parseSource(String value) {
        if (value == null || value.isBlank()) return RequirementSource.MANUAL;
        try {
            return RequirementSource.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("不支持的需求来源：" + value);
        }
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
