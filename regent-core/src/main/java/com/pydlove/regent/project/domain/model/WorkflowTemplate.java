package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.List;

/** 全局研发流程模板版本及其阶段配置。 */
public record WorkflowTemplate(
        WorkflowTemplateId id,
        String code,
        String name,
        int versionNo,
        String description,
        WorkflowTemplateStatus status,
        List<WorkflowTemplateStage> stages) {

    public WorkflowTemplate {
        if (id == null
                || code == null
                || code.isBlank()
                || name == null
                || name.isBlank()
                || status == null) {
            throw new DomainException("流程模板信息不完整");
        }
        if (versionNo <= 0) {
            throw new DomainException("流程模板版本号必须大于 0");
        }
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public String displayName() {
        return name + " V" + versionNo;
    }

    public boolean selectable() {
        return status == WorkflowTemplateStatus.PUBLISHED;
    }

    public WorkflowTemplate superseded() {
        if (status != WorkflowTemplateStatus.PUBLISHED) {
            throw new DomainException("只有当前发布版本可以标记为已替代");
        }
        return new WorkflowTemplate(
                id, code, name, versionNo, description,
                WorkflowTemplateStatus.SUPERSEDED, stages);
    }

    public WorkflowTemplate archived() {
        if (status == WorkflowTemplateStatus.ARCHIVED) return this;
        return new WorkflowTemplate(
                id, code, name, versionNo, description,
                WorkflowTemplateStatus.ARCHIVED, stages);
    }
}
