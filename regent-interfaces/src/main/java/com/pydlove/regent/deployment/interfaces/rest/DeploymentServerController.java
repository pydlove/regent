package com.pydlove.regent.deployment.interfaces.rest;

import com.pydlove.regent.deployment.application.command.DeploymentServerCommandService;
import com.pydlove.regent.deployment.application.command.SaveDeploymentServerCommand;
import com.pydlove.regent.deployment.application.query.DeploymentServerQueryService;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentServerResponse;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentServerSaveRequest;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.application.query.ProjectAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.LinkedHashSet;
import java.util.List;

/** 可复用部署服务器接口。 */
@RestController
@RequestMapping("/api/deployment-servers")
public class DeploymentServerController {

    private final DeploymentServerCommandService commandService;
    private final DeploymentServerQueryService queryService;
    private final ProjectAccessService projectAccessService;

    public DeploymentServerController(DeploymentServerCommandService commandService,
                                      DeploymentServerQueryService queryService,
                                      ProjectAccessService projectAccessService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    public List<DeploymentServerResponse> list(
            @RequestParam(required = false) Long projectId,
            HttpServletRequest request) {
        ProjectId owner = projectId == null ? null : ProjectId.of(projectId);
        return queryService.list(owner, currentUser(request)).stream()
                .map(DeploymentServerResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public DeploymentServerResponse detail(@PathVariable long id, HttpServletRequest request) {
        return DeploymentServerResponse.from(
                queryService.get(DeploymentServerId.of(id), currentUser(request)));
    }

    @PostMapping
    public DeploymentServerResponse create(@RequestBody DeploymentServerSaveRequest request,
                                           HttpServletRequest servletRequest) {
        UserId ownerId = currentUser(servletRequest);
        List<ProjectId> projectIds = requireProjects(request.projectIds(), ownerId);
        var created = commandService.create(toCommand(request, projectIds));
        return DeploymentServerResponse.from(queryService.get(created.getId(), ownerId));
    }

    @PutMapping("/{id}")
    public DeploymentServerResponse update(@PathVariable long id,
                                           @RequestBody DeploymentServerSaveRequest request,
                                           HttpServletRequest servletRequest) {
        UserId ownerId = currentUser(servletRequest);
        queryService.get(DeploymentServerId.of(id), ownerId);
        List<ProjectId> projectIds = requireProjects(request.projectIds(), ownerId);
        var updated = commandService.update(
                DeploymentServerId.of(id), toCommand(request, projectIds));
        return DeploymentServerResponse.from(queryService.get(updated.getId(), ownerId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
        queryService.get(DeploymentServerId.of(id), currentUser(request));
        commandService.delete(DeploymentServerId.of(id));
        return ResponseEntity.noContent().build();
    }

    private List<ProjectId> requireProjects(List<Long> projectIds, UserId ownerId) {
        if (projectIds == null || projectIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个归属项目");
        }
        LinkedHashSet<ProjectId> unique = new LinkedHashSet<>();
        for (Long projectId : projectIds) {
            if (projectId == null) {
                throw new IllegalArgumentException("归属项目不能为空");
            }
            unique.add(ProjectId.of(projectId));
        }
        unique.forEach(projectId -> projectAccessService.require(projectId, ownerId));
        return List.copyOf(unique);
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }

    private static SaveDeploymentServerCommand toCommand(DeploymentServerSaveRequest request,
                                                         List<ProjectId> projectIds) {
        if (request.port() == null) {
            throw new IllegalArgumentException("缺少 port");
        }
        return new SaveDeploymentServerCommand(
                projectIds,
                request.name(),
                request.host(),
                request.port(),
                request.username(),
                DeploymentAuthType.parse(request.authType()),
                request.defaultRemoteDirectory(),
                request.password());
    }
}
