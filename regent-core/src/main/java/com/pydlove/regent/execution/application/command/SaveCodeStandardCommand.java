package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.CodeStandardScope;
import com.pydlove.regent.project.domain.model.ProjectId;

/** 新建或更新开发规范元数据。 */
public record SaveCodeStandardCommand(
        ProjectId projectId,
        String name,
        String description,
        CodeStandardScope scope,
        boolean enabled,
        boolean defaultApply,
        int sortOrder) {
}
