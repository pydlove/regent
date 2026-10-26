package com.pydlove.regent.project.domain.repository;

import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.WorkflowTemplate;
import com.pydlove.regent.project.domain.model.WorkflowTemplateId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 流程模板目录与项目/版本绑定。 */
public interface WorkflowTemplateRepository {

    List<WorkflowTemplate> findPublished();

    Optional<WorkflowTemplate> findById(WorkflowTemplateId id);

    Optional<WorkflowTemplate> findDefault();

    Optional<WorkflowTemplateId> findProjectTemplate(ProjectId projectId);

    Optional<WorkflowTemplateId> findVersionTemplate(ProductVersionId versionId);

    void bindProject(ProjectId projectId, WorkflowTemplateId templateId, Instant selectedAt);

    void bindVersion(ProductVersionId versionId, WorkflowTemplateId templateId, Instant snappedAt);
}
