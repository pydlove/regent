package com.pydlove.regent.project.infrastructure.persistence;

import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.WorkflowTemplate;
import com.pydlove.regent.project.domain.model.WorkflowTemplateDraft;
import com.pydlove.regent.project.domain.model.WorkflowTemplateId;
import com.pydlove.regent.project.domain.model.WorkflowTemplateStage;
import com.pydlove.regent.project.domain.model.WorkflowTemplateStatus;
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
    public List<WorkflowTemplate> findAll() {
        return mapper.selectAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<WorkflowTemplate> findPublished() {
        return mapper.selectPublished().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<WorkflowTemplate> findById(WorkflowTemplateId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public Optional<WorkflowTemplate> findDefault() {
        return Optional.ofNullable(mapper.selectDefault()).map(this::toDomain);
    }

    @Override
    public WorkflowTemplate create(WorkflowTemplateDraft draft, Instant createdAt) {
        WorkflowTemplateData data = new WorkflowTemplateData();
        data.setTemplateCode(draft.code());
        data.setVersionNo(draft.versionNo());
        data.setName(draft.name());
        data.setDescription(draft.description());
        data.setStatus(WorkflowTemplateStatus.PUBLISHED.name());
        if (mapper.insertTemplate(data) < 1 || data.getId() == null) {
            throw new DomainException("流程模板保存失败");
        }
        for (WorkflowTemplateStage stage : draft.stages()) {
            WorkflowTemplateStageData stageData = new WorkflowTemplateStageData();
            stageData.setTemplateId(data.getId());
            stageData.setStageCode(stage.code());
            stageData.setStageName(stage.name());
            stageData.setStageOrder(stage.order());
            stageData.setScopeCode(stage.scope());
            stageData.setApproverRole(stage.approverRole());
            stageData.setDescription(stage.description());
            if (mapper.insertStage(stageData) < 1) {
                throw new DomainException("流程阶段保存失败：" + stage.name());
            }
        }
        return new WorkflowTemplate(
                WorkflowTemplateId.of(data.getId()),
                draft.code(),
                draft.name(),
                draft.versionNo(),
                draft.description(),
                WorkflowTemplateStatus.PUBLISHED,
                draft.stages());
    }

    @Override
    public void supersedePublished(String code, Instant updatedAt) {
        mapper.supersedePublished(code, db(updatedAt));
    }

    @Override
    public int nextVersionNo(String code) {
        return mapper.selectNextVersionNo(code);
    }

    @Override
    public int countBindings(WorkflowTemplateId templateId) {
        return mapper.countBindings(templateId.value());
    }

    @Override
    public void updateStatus(WorkflowTemplate template, Instant updatedAt) {
        if (mapper.updateStatus(
                template.id().value(), template.status().name(), db(updatedAt)) < 1) {
            throw new DomainException("流程模板状态更新失败");
        }
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
                WorkflowTemplateStatus.valueOf(data.getStatus()),
                stages);
    }

    private static LocalDateTime db(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
