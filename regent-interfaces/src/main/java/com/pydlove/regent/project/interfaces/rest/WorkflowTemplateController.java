package com.pydlove.regent.project.interfaces.rest;

import com.pydlove.regent.project.application.query.WorkflowTemplateQueryService;
import com.pydlove.regent.project.interfaces.dto.WorkflowTemplateResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 全局研发流程模板目录。 */
@RestController
@RequestMapping("/api/workflow-templates")
public class WorkflowTemplateController {

    private final WorkflowTemplateQueryService queryService;

    public WorkflowTemplateController(WorkflowTemplateQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<WorkflowTemplateResponse> list() {
        return queryService.list().stream()
                .map(WorkflowTemplateResponse::from)
                .toList();
    }
}
