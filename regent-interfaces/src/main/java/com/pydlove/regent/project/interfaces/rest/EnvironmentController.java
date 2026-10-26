package com.pydlove.regent.project.interfaces.rest;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.application.command.EnvironmentEntryService;
import com.pydlove.regent.project.application.command.SaveEnvironmentCommand;
import com.pydlove.regent.project.domain.model.EnvironmentId;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.interfaces.dto.EnvironmentRequest;
import com.pydlove.regent.project.interfaces.dto.EnvironmentResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 环境信息管理接口。 */
@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    private final EnvironmentEntryService environmentEntryService;

    public EnvironmentController(EnvironmentEntryService environmentEntryService) {
        this.environmentEntryService = environmentEntryService;
    }

    @GetMapping
    public List<EnvironmentResponse> list(HttpServletRequest request) {
        return environmentEntryService.list(currentUser(request)).stream()
                .map(EnvironmentResponse::from)
                .toList();
    }

    @PostMapping
    public EnvironmentResponse create(@RequestBody EnvironmentRequest request,
                                      HttpServletRequest servletRequest) {
        return EnvironmentResponse.from(
                environmentEntryService.create(toCommand(request), currentUser(servletRequest)));
    }

    @PutMapping("/{id}")
    public EnvironmentResponse update(@PathVariable long id,
                                      @RequestBody EnvironmentRequest request,
                                      HttpServletRequest servletRequest) {
        return EnvironmentResponse.from(
                environmentEntryService.update(EnvironmentId.of(id), toCommand(request),
                        currentUser(servletRequest)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
        environmentEntryService.delete(EnvironmentId.of(id), currentUser(request));
        return ResponseEntity.noContent().build();
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }

    private static SaveEnvironmentCommand toCommand(EnvironmentRequest request) {
        EnvironmentScope scope = EnvironmentScope.parse(request.scope());
        List<Long> rawProjectIds = request.projectIds() == null || request.projectIds().isEmpty()
                ? (request.projectId() == null ? List.of() : List.of(request.projectId()))
                : request.projectIds();
        return new SaveEnvironmentCommand(scope,
                rawProjectIds.stream().map(ProjectId::of).toList(), request.value(),
                request.tags(), request.description(), Boolean.TRUE.equals(request.secret()));
    }
}
