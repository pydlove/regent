package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;

/** 新建或整体更新一条环境信息。环境键由应用服务生成，不从命令传入。 */
public record SaveEnvironmentCommand(
        EnvironmentScope scope,
        List<ProjectId> projectIds,
        String value,
        List<String> tags,
        String description,
        boolean secret) {

    public SaveEnvironmentCommand {
        projectIds = projectIds == null ? List.of() : List.copyOf(projectIds);
    }

    public SaveEnvironmentCommand(EnvironmentScope scope,
                                  ProjectId projectId,
                                  String value,
                                  List<String> tags,
                                  String description,
                                  boolean secret) {
        this(scope, projectId == null ? List.of() : List.of(projectId),
                value, tags, description, secret);
    }

    public ProjectId projectId() {
        return projectIds.isEmpty() ? null : projectIds.get(0);
    }
}
