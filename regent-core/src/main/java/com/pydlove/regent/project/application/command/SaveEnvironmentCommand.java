package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;

/** 新建或整体更新一条环境信息。 */
public record SaveEnvironmentCommand(
        EnvironmentScope scope,
        ProjectId projectId,
        String key,
        String value,
        List<String> tags,
        String description) {
}
