package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.TaskDraft;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.time.Instant;
import java.util.List;

/** 当前用户可继续编辑的任务草稿。 */
public record TaskDraftResponse(
        long id,
        Long projectId,
        String prompt,
        int priority,
        List<Long> pluginIds,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskDraftResponse from(TaskDraft draft) {
        return new TaskDraftResponse(
                draft.getId().value(),
                draft.getProjectId().map(ProjectId::value).orElse(null),
                draft.getPrompt(),
                draft.getPriority(),
                draft.getPluginIds().stream().map(PluginId::value).toList(),
                draft.getCreatedAt(),
                draft.getUpdatedAt());
    }
}
