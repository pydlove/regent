package com.pydlove.regent.project.application.command;

import java.util.List;

/** 新建全局流程模板，或从既有模板复制出一个新版本。 */
public record SaveWorkflowTemplateCommand(
        String name,
        String description,
        List<Stage> stages) {

    public SaveWorkflowTemplateCommand {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public record Stage(
            String code,
            String name,
            String scope,
            String approverRole,
            String description) {
    }
}
