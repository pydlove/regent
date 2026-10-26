package com.pydlove.regent.project.interfaces.dto;

import com.pydlove.regent.project.domain.model.EnvironmentEntry;

import java.time.Instant;
import java.util.List;

/** 环境信息的对外契约。 */
public record EnvironmentResponse(
        long id,
        String scope,
        Long projectId,
        List<Long> projectIds,
        String key,
        String value,
        List<String> tags,
        String description,
        boolean secret,
        Instant createdAt,
        Instant updatedAt) {

    public static EnvironmentResponse from(EnvironmentEntry entry) {
        return new EnvironmentResponse(
                entry.getId().value(),
                entry.getScope().name(),
                entry.getProjectId() == null ? null : entry.getProjectId().value(),
                entry.getProjectIds().stream().map(projectId -> projectId.value()).toList(),
                entry.getKey(),
                entry.getValue(),
                entry.getTags(),
                entry.getDescription(),
                entry.isSecret(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
