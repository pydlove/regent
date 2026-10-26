package com.pydlove.regent.delivery.interfaces.rest;

import com.pydlove.regent.delivery.application.command.ProductVersionCommandService;
import com.pydlove.regent.delivery.application.query.ProductVersionQueryService;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.interfaces.dto.ProductVersionResponse;
import com.pydlove.regent.delivery.interfaces.dto.ProductVersionSaveRequest;
import com.pydlove.regent.delivery.interfaces.dto.ProductVersionUpdateRequest;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.WorkflowTemplateId;
import com.pydlove.regent.project.application.query.WorkflowTemplateQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.List;

/** 项目级产品版本规划接口。 */
@RestController
@RequestMapping("/api/product-versions")
public class ProductVersionController {

    private final ProductVersionCommandService commandService;
    private final ProductVersionQueryService queryService;
    private final WorkflowTemplateQueryService workflowTemplateQueryService;

    public ProductVersionController(ProductVersionCommandService commandService,
                                    ProductVersionQueryService queryService,
                                    WorkflowTemplateQueryService workflowTemplateQueryService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.workflowTemplateQueryService = workflowTemplateQueryService;
    }

    @GetMapping
    public List<ProductVersionResponse> list(@RequestParam long projectId,
                                             HttpServletRequest request) {
        return queryService.list(ProjectId.of(projectId), currentUser(request)).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public ProductVersionResponse create(@RequestBody ProductVersionSaveRequest body,
                                         HttpServletRequest request) {
        if (body.projectId() == null) {
            throw new IllegalArgumentException("缺少 projectId");
        }
        UserId actorId = currentUser(request);
        var version = commandService.create(
                ProjectId.of(body.projectId()),
                body.versionNo(),
                body.name(),
                body.description(),
                actorId);
        return toResponse(queryService.get(version.getId(), actorId));
    }

    @PutMapping("/{id}")
    public ProductVersionResponse update(@PathVariable long id,
                                         @RequestBody ProductVersionUpdateRequest body,
                                         HttpServletRequest request) {
        UserId actorId = currentUser(request);
        var version = commandService.update(
                ProductVersionId.of(id), null, body.description(), actorId);
        return toResponse(queryService.get(version.getId(), actorId));
    }

    @PostMapping("/{id}/close")
    public ProductVersionResponse close(@PathVariable long id,
                                        HttpServletRequest request) {
        UserId actorId = currentUser(request);
        var version = commandService.close(ProductVersionId.of(id), actorId);
        return toResponse(queryService.get(version.getId(), actorId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id,
                                       HttpServletRequest request) {
        commandService.delete(ProductVersionId.of(id), currentUser(request));
        return ResponseEntity.noContent().build();
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }

    private ProductVersionResponse toResponse(com.pydlove.regent.delivery.domain.model.ProductVersion version) {
        WorkflowTemplateId templateId = workflowTemplateQueryService.versionTemplateId(version.getId());
        return ProductVersionResponse.from(
                version,
                templateId == null ? null : templateId.value());
    }
}
