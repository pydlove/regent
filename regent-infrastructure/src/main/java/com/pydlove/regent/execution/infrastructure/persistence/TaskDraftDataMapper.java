package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.TaskDraft;
import com.pydlove.regent.execution.domain.model.TaskDraftId;
import com.pydlove.regent.execution.domain.model.TaskDraftSnapshot;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 任务草稿领域对象与数据对象之间的转换。 */
public final class TaskDraftDataMapper {

    private TaskDraftDataMapper() {
    }

    public static TaskDraftData toData(TaskDraft draft, String pluginIdsJson) {
        TaskDraftData data = new TaskDraftData();
        data.setId(draft.getId() == null ? null : draft.getId().value());
        data.setUserId(draft.getUserId().value());
        data.setProjectId(draft.getProjectId().map(ProjectId::value).orElse(null));
        data.setPrompt(draft.getPrompt());
        data.setPriority(draft.getPriority());
        data.setPluginIds(pluginIdsJson);
        data.setCreatedAt(toDb(draft.getCreatedAt()));
        data.setUpdatedAt(toDb(draft.getUpdatedAt()));
        return data;
    }

    public static TaskDraft toDomain(TaskDraftData data, List<PluginId> pluginIds) {
        return TaskDraft.restore(new TaskDraftSnapshot(
                TaskDraftId.of(data.getId()),
                UserId.of(data.getUserId()),
                data.getProjectId() == null ? null : ProjectId.of(data.getProjectId()),
                data.getPrompt(),
                data.getPriority(),
                pluginIds,
                fromDb(data.getCreatedAt()),
                fromDb(data.getUpdatedAt())));
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
