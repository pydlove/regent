package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.time.Instant;
import java.util.List;

/** 从持久化数据重建任务草稿所需的完整快照。 */
public record TaskDraftSnapshot(
        TaskDraftId id,
        UserId userId,
        ProjectId projectId,
        String prompt,
        int priority,
        List<PluginId> pluginIds,
        Instant createdAt,
        Instant updatedAt) {
}
