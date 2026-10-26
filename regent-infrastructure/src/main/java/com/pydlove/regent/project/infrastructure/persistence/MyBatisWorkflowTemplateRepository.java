package com.pydlove.regent.project.infrastructure.persistence;

import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.WorkflowTemplate;
import com.pydlove.regent.project.domain.model.WorkflowTemplateId;
import com.pydlove.regent.project.domain.model.WorkflowTemplateStage;
import com.pydlove.regent.project.domain.repository.WorkflowTemplateRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 流程模板目录与绑定的 MyBatis 实现。 */
@Repository
public class MyBatisWorkflowTemplateRepository implements WorkflowTemplateRepository {

    private final WorkflowTemplateMapper mapper;

    public MyBatisWorkflowTemplateRepository(WorkflowTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<WorkflowTemplate> findPublished() {
        return mapper.selectPublished().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<WorkflowTemplate> findById(WorkflowTemplateId id) {
        return Optional.ofNullable(mapper.selectPublishedById(id.value())).map(this::toDomain);
    }

    @Override
    public Optional<WorkflowTemplate> findDefault() {
        return Optional.ofNullable(mapper.selectDefault()).map(this::toDomain);
    }

    @Override
    public Optional<WorkflowTemplateId> findProjectTemplate(ProjectId projectId) {
        return Optional.ofNullable(mapper.selectProjectTemplateId(projectId.value()))
                .map(WorkflowTemplateId::of);
    }

    @Override
    public Optional<WorkflowTemplateId> findVersionTemplate(ProductVersionId versionId) {
        return Optional.ofNullable(mapper.selectVersionTemplateId(versionId.value()))
                .map(WorkflowTemplateId::of);
    }

    @Override
    public void bindProject(ProjectId projectId,
                            WorkflowTemplateId templateId,
                            Instant selectedAt) {
        if (mapper.bindProject(projectId.value(), templateId.value(), db(selectedAt)) < 1) {
            throw new DomainException("项目流程模板绑定失败");
        }
    }

    @Override
    public void bindVersion(ProductVersionId versionId,
                            WorkflowTemplateId templateId,
                            Instant snappedAt) {
        if (mapper.bindVersion(versionId.value(), templateId.value(), db(snappedAt)) < 1) {
            throw new DomainException("产品版本流程模板快照失败");
        }
    }

    private WorkflowTemplate toDomain(WorkflowTemplateData data) {
        List<WorkflowTemplateStage> stages = mapper.selectStages(data.getId()).stream()
                .map(stage -> new WorkflowTemplateStage(
                        stage.getStageCode(),
                        stage.getStageName(),
                        stage.getStageOrder(),
                        stage.getScopeCode(),
                        stage.getApproverRole(),
                        stage.getDescription()))
                .toList();
        return new WorkflowTemplate(
                WorkflowTemplateId.of(data.getId()),
                data.getTemplateCode(),
                data.getName(),
                data.getVersionNo(),
                data.getDescription(),
                stages);
    }

    private static LocalDateTime db(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
