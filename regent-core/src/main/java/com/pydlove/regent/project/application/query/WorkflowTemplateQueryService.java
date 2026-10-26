package com.pydlove.regent.project.application.query;

import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.WorkflowTemplate;
import com.pydlove.regent.project.domain.model.WorkflowTemplateId;
import com.pydlove.regent.project.domain.repository.WorkflowTemplateRepository;

import java.util.List;

/** 流程图模板目录与绑定查询。 */
public class WorkflowTemplateQueryService {

    private final WorkflowTemplateRepository repository;

    public WorkflowTemplateQueryService(WorkflowTemplateRepository repository) {
        this.repository = repository;
    }

    public List<WorkflowTemplate> list() {
        return repository.findAll();
    }

    public WorkflowTemplateId projectTemplateId(ProjectId projectId) {
        return repository.findProjectTemplate(projectId)
                .orElseGet(() -> repository.findDefault()
                        .map(WorkflowTemplate::id)
                        .orElse(null));
    }

    public WorkflowTemplateId versionTemplateId(ProductVersionId versionId) {
        return repository.findVersionTemplate(versionId)
                .orElseGet(() -> repository.findDefault()
                        .map(WorkflowTemplate::id)
                        .orElse(null));
    }
}
