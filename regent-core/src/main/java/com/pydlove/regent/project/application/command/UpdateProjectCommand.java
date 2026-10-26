package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.ProjectVersionSource;
import com.pydlove.regent.shared.kernel.DecisionLevel;

/**
 * 编辑项目画像的入参。项目标识不可修改。
 */
public record UpdateProjectCommand(
        String name,
        String path,
        DecisionLevel defaultDecisionLevel,
        ProjectVersionSource versionSource,
        String fallbackVersion,
        boolean enabled) {

    public UpdateProjectCommand(String name,
                                String path,
                                DecisionLevel defaultDecisionLevel,
                                boolean enabled) {
        this(name, path, defaultDecisionLevel, ProjectVersionSource.GIT_TAG, null, enabled);
    }
}
