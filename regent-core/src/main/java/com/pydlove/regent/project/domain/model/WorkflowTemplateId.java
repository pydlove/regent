package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 研发流程模板版本 ID。 */
public record WorkflowTemplateId(long value) {

    public WorkflowTemplateId {
        if (value <= 0) {
            throw new DomainException("流程模板 ID 必须为正数：" + value);
        }
    }

    public static WorkflowTemplateId of(long value) {
        return new WorkflowTemplateId(value);
    }
}
