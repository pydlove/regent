package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;

/** 自动保存草稿的完整表单快照。 */
public record SaveTaskDraftCommand(
        ProjectId projectId,
        String prompt,
        int priority,
        List<PluginId> pluginIds) {
}
