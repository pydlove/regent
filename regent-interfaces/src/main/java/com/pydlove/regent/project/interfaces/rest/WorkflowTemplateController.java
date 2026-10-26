package com.pydlove.regent.project.interfaces.rest;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.organization.application.query.OrganizationAccessService;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.project.application.command.WorkflowTemplateCommandService;
import com.pydlove.regent.project.application.query.WorkflowTemplateQueryService;
import com.pydlove.regent.project.domain.model.WorkflowTemplateId;
import com.pydlove.regent.project.interfaces.dto.WorkflowTemplateResponse;
import com.pydlove.regent.project.interfaces.dto.WorkflowTemplateSaveRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 全局研发流程模板目录。 */
@RestController
@RequestMapping("/api/workflow-templates")
public class WorkflowTemplateController {

    private final WorkflowTemplateQueryService queryService;
    private final WorkflowTemplateCommandService commandService;
    private final OrganizationAccessService accessService;

    public WorkflowTemplateController(WorkflowTemplateQueryService queryService,
                                      WorkflowTemplateCommandService commandService,
                                      OrganizationAccessService accessService) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.accessService = accessService;
    }

    @GetMapping
    public List<WorkflowTemplateResponse> list() {
        return queryService.list().stream()
                .map(WorkflowTemplateResponse::from)
                .toList();
    }

    /**
     * 新建全局模板。
     *
     * <p>模板是部署级共享资源；当前版本以访问者所在组织的管理员权限作为管理门槛。
     * 后续如果引入平台管理员，再把这里替换成平台级权限判断。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowTemplateResponse create(
            @RequestParam long organizationId,
            @RequestBody WorkflowTemplateSaveRequest request,
            HttpServletRequest servletRequest) {
        requireAdmin(organizationId, servletRequest);
        return WorkflowTemplateResponse.from(
                commandService.create(request.toCommand()));
    }

    /** 从当前模板复制一个可编辑的新版本；旧版本自动变为“已替代”。 */
    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowTemplateResponse createVersion(
            @PathVariable long id,
            @RequestParam long organizationId,
            @RequestBody WorkflowTemplateSaveRequest request,
            HttpServletRequest servletRequest) {
        requireAdmin(organizationId, servletRequest);
        return WorkflowTemplateResponse.from(
                commandService.createVersion(
                        WorkflowTemplateId.of(id), request.toCommand()));
    }

    /** 停用不再用于新项目的模板版本。已被项目或版本绑定时拒绝。 */
    @PostMapping("/{id}/archive")
    public WorkflowTemplateResponse archive(
            @PathVariable long id,
            @RequestParam long organizationId,
            HttpServletRequest servletRequest) {
        requireAdmin(organizationId, servletRequest);
        return WorkflowTemplateResponse.from(
                commandService.archive(WorkflowTemplateId.of(id)));
    }

    private void requireAdmin(long organizationId, HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        accessService.requireAdmin(OrganizationId.of(organizationId), UserId.of(user.id()));
    }
}
