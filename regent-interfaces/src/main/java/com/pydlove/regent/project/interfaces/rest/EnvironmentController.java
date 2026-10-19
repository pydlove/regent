package com.pydlove.regent.project.interfaces.rest;

import com.pydlove.regent.project.application.command.EnvironmentEntryService;
import com.pydlove.regent.project.application.command.SaveEnvironmentCommand;
import com.pydlove.regent.project.domain.model.EnvironmentId;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.interfaces.dto.EnvironmentRequest;
import com.pydlove.regent.project.interfaces.dto.EnvironmentResponse;
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
    public List<EnvironmentResponse> list() {
        return environmentEntryService.list().stream().map(EnvironmentResponse::from).toList();
    }

    @PostMapping
    public EnvironmentResponse create(@RequestBody EnvironmentRequest request) {
        return EnvironmentResponse.from(environmentEntryService.create(toCommand(request)));
    }

    @PutMapping("/{id}")
    public EnvironmentResponse update(@PathVariable long id, @RequestBody EnvironmentRequest request) {
        return EnvironmentResponse.from(
                environmentEntryService.update(EnvironmentId.of(id), toCommand(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        environmentEntryService.delete(EnvironmentId.of(id));
        return ResponseEntity.noContent().build();
    }

    private static SaveEnvironmentCommand toCommand(EnvironmentRequest request) {
        EnvironmentScope scope = EnvironmentScope.parse(request.scope());
        ProjectId projectId = request.projectId() == null ? null : ProjectId.of(request.projectId());
        return new SaveEnvironmentCommand(scope, projectId, request.value(),
                request.tags(), request.description(), Boolean.TRUE.equals(request.secret()));
    }
}
