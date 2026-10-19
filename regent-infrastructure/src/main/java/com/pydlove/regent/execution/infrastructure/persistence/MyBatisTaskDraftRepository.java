package com.pydlove.regent.execution.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.execution.domain.model.TaskDraft;
import com.pydlove.regent.execution.domain.model.TaskDraftId;
import com.pydlove.regent.execution.domain.repository.TaskDraftRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.plugin.domain.model.PluginId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 任务草稿仓储的 MyBatis 实现。 */
@Repository
public class MyBatisTaskDraftRepository implements TaskDraftRepository {

    private static final TypeReference<List<Long>> PLUGIN_IDS_TYPE = new TypeReference<>() {
    };

    private final TaskDraftMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisTaskDraftRepository(TaskDraftMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<TaskDraft> findByUserId(UserId userId) {
        return Optional.ofNullable(mapper.selectByUserId(userId.value()))
                .map(this::toDomain);
    }

    @Override
    public void save(TaskDraft draft) {
        mapper.upsert(TaskDraftDataMapper.toData(draft, writePluginIds(draft.getPluginIds())));
        if (draft.getId() == null) {
            TaskDraftData stored = mapper.selectByUserId(draft.getUserId().value());
            if (stored == null) {
                throw new IllegalStateException("任务草稿保存后未能重新读取：" + draft.getUserId());
            }
            draft.assignIdentity(TaskDraftId.of(stored.getId()));
        }
    }

    @Override
    public void deleteByUserId(UserId userId) {
        mapper.deleteByUserId(userId.value());
    }

    private TaskDraft toDomain(TaskDraftData data) {
        return TaskDraftDataMapper.toDomain(
                data, readPluginIds(data.getPluginIds()).stream().map(PluginId::of).toList());
    }

    private String writePluginIds(List<PluginId> pluginIds) {
        try {
            return objectMapper.writeValueAsString(pluginIds.stream().map(PluginId::value).toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("任务草稿插件 ID 序列化失败", e);
        }
    }

    private List<Long> readPluginIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, PLUGIN_IDS_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("任务草稿插件 ID 反序列化失败", e);
        }
    }
}
