package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;

/** 新建或整体更新一条环境信息。环境键由应用服务生成，不从命令传入。 */
public record SaveEnvironmentCommand(
        EnvironmentScope scope,
        ProjectId projectId,
        String value,
        List<String> tags,
        String description,
        boolean secret) {
}
