package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.List;

/** 已发布的研发流程模板版本及其阶段配置。 */
public record WorkflowTemplate(
        WorkflowTemplateId id,
        String code,
        String name,
        int versionNo,
        String description,
        List<WorkflowTemplateStage> stages) {

    public WorkflowTemplate {
        if (id == null || code == null || code.isBlank() || name == null || name.isBlank()) {
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
}
