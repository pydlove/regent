package com.pydlove.regent.project.interfaces.dto;

import com.pydlove.regent.project.application.command.SaveWorkflowTemplateCommand;

import java.util.List;

/** 新建流程模板或复制新版本的请求。 */
public record WorkflowTemplateSaveRequest(
        String name,
        String description,
        List<StageRequest> stages) {

    public SaveWorkflowTemplateCommand toCommand() {
        return new SaveWorkflowTemplateCommand(
                name,
                description,
                stages == null
                        ? List.of()
                        : stages.stream()
                                .map(stage -> new SaveWorkflowTemplateCommand.Stage(
                                        stage.code(),
                                        stage.name(),
                                        stage.scope(),
                                        stage.approverRole(),
                                        stage.description()))
                                .toList());
    }

    public record StageRequest(
            String code,
            String name,
            String scope,
            String approverRole,
            String description) {
    }
}
