package com.pydlove.regent.project.interfaces.rest;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.application.command.ProjectMemberCommandService;
import com.pydlove.regent.project.application.command.ProjectMemberRoleAssignment;
import com.pydlove.regent.project.application.query.ProjectMemberQueryService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.interfaces.dto.ProjectMemberAddRequest;
import com.pydlove.regent.project.interfaces.dto.ProjectMemberBatchAddRequest;
import com.pydlove.regent.project.interfaces.dto.ProjectMemberCandidateResponse;
import com.pydlove.regent.project.interfaces.dto.ProjectMemberResponse;
import com.pydlove.regent.project.interfaces.dto.ProjectMemberRolesUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class ProjectMemberController {

    private final ProjectMemberCommandService commandService;
    private final ProjectMemberQueryService queryService;

    public ProjectMemberController(ProjectMemberCommandService commandService,
                                   ProjectMemberQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public List<ProjectMemberResponse> list(@PathVariable long projectId,
                                            HttpServletRequest request) {
        Map<Long, MutableMember> grouped = new LinkedHashMap<>();
        for (var view : queryService.list(ProjectId.of(projectId), currentUser(request))) {
            MutableMember member = grouped.computeIfAbsent(view.member().userId().value(), id ->
                    new MutableMember(
                            id,
                            view.username(),
                            view.displayName(),
                            view.email(),
                            view.avatarVersion()));
            member.roles.add(view.member().role().name());
        }
        return grouped.values().stream()
                .map(member -> new ProjectMemberResponse(
                        member.userId,
                        member.username,
                        member.displayName,
                        member.email,
                        avatarUrl(member.userId, member.avatarVersion),
                        member.roles))
                .toList();
    }

    @GetMapping("/candidates")
    public List<ProjectMemberCandidateResponse> candidates(@PathVariable long projectId,
                                                           HttpServletRequest request) {
        return queryService.candidates(ProjectId.of(projectId), currentUser(request)).stream()
                .map(ProjectMemberCandidateResponse::from)
                .toList();
    }

    @PostMapping
    public List<ProjectMemberResponse> add(@PathVariable long projectId,
                                           @RequestBody ProjectMemberAddRequest request,
                                           HttpServletRequest servletRequest) {
        ProjectMemberRole role;
        try {
            role = ProjectMemberRole.valueOf(request.role());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("不支持的项目角色：" + request.role());
        }
        commandService.add(ProjectId.of(projectId), request.email(), role,
                currentUser(servletRequest));
        return list(projectId, servletRequest);
    }

    @PostMapping("/batch")
    public List<ProjectMemberResponse> addMany(
            @PathVariable long projectId,
            @RequestBody ProjectMemberBatchAddRequest request,
            HttpServletRequest servletRequest) {
        commandService.addAssignments(
                ProjectId.of(projectId),
                request.members() == null
                        ? List.of()
                        : request.members().stream()
                        .map(member -> new ProjectMemberRoleAssignment(
                                member.email(),
                                member.roles() == null
                                        ? List.of()
                                        : member.roles().stream()
                                        .map(ProjectMemberController::parseRole)
                                        .toList()))
                        .toList(),
                currentUser(servletRequest));
        return list(projectId, servletRequest);
    }

    @PutMapping("/{userId}/roles")
    public List<ProjectMemberResponse> updateRoles(
            @PathVariable long projectId,
            @PathVariable long userId,
            @RequestBody ProjectMemberRolesUpdateRequest request,
            HttpServletRequest servletRequest) {
        List<ProjectMemberRole> roles = request.roles() == null
                ? List.of()
                : request.roles().stream().map(ProjectMemberController::parseRole).toList();
        commandService.updateRoles(
                ProjectId.of(projectId), UserId.of(userId), roles, currentUser(servletRequest));
        return list(projectId, servletRequest);
    }

    @DeleteMapping("/{userId}/{role}")
    public List<ProjectMemberResponse> remove(@PathVariable long projectId,
                                              @PathVariable long userId,
                                              @PathVariable String role,
                                              HttpServletRequest request) {
        ProjectMemberRole parsed = parseRole(role);
        commandService.remove(ProjectId.of(projectId), UserId.of(userId), parsed,
                currentUser(request));
        return list(projectId, request);
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }

    private static ProjectMemberRole parseRole(String role) {
        try {
            return ProjectMemberRole.valueOf(role);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("不支持的项目角色：" + role);
        }
    }

    private static String avatarUrl(long userId, Long avatarVersion) {
        return avatarVersion == null
                ? null
                : "/api/profile/avatar/" + userId + "?v=" + avatarVersion;
    }

    private static final class MutableMember {
        private final long userId;
        private final String username;
        private final String displayName;
        private final String email;
        private final Long avatarVersion;
        private final List<String> roles = new ArrayList<>();

        private MutableMember(long userId,
                              String username,
                              String displayName,
                              String email,
                              Long avatarVersion) {
            this.userId = userId;
            this.username = username;
            this.displayName = displayName;
            this.email = email;
            this.avatarVersion = avatarVersion;
        }
    }
}
