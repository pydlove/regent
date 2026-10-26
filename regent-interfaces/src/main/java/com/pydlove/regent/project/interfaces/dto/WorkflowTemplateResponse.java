package com.pydlove.regent.project.interfaces.dto;

import com.pydlove.regent.project.domain.model.WorkflowTemplate;
import com.pydlove.regent.project.domain.model.WorkflowTemplateStage;

import java.util.List;

/** 全局研发流程模板及阶段配置。 */
public record WorkflowTemplateResponse(
        long id,
        String code,
        String name,
        int versionNo,
        String displayName,
        String description,
        String status,
        List<StageResponse> stages) {

    public static WorkflowTemplateResponse from(WorkflowTemplate template) {
        return new WorkflowTemplateResponse(
                template.id().value(),
                template.code(),
                template.name(),
                template.versionNo(),
                template.displayName(),
                template.description(),
                template.status().name(),
                template.stages().stream().map(StageResponse::from).toList());
    }

    public record StageResponse(
            String code,
            String name,
            int order,
            String scope,
            String approverRole,
            String description) {

        public static StageResponse from(WorkflowTemplateStage stage) {
            return new StageResponse(
                    stage.code(),
                    stage.name(),
                    stage.order(),
                    stage.scope(),
                    stage.approverRole(),
                    stage.description());
        }
    }
}
