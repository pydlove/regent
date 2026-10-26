package com.pydlove.regent.project.domain.model;

/** 研发流程模板中的一个阶段或门禁。 */
public record WorkflowTemplateStage(
        String code,
        String name,
        int order,
        String scope,
        String approverRole,
        String description) {
}
