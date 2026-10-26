package com.pydlove.regent.project.infrastructure.persistence;

import lombok.Data;

/** workflow_template_stage 表读写对象。 */
@Data
public class WorkflowTemplateStageData {
    private Long id;
    private Long templateId;
    private String stageCode;
    private String stageName;
    private Integer stageOrder;
    private String scopeCode;
    private String approverRole;
    private String description;
}
