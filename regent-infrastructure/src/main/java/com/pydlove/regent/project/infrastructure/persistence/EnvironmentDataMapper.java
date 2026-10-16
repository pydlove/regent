package com.pydlove.regent.project.infrastructure.persistence;

import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentId;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

/** 环境领域对象与数据对象之间的转换。 */
public final class EnvironmentDataMapper {

    private EnvironmentDataMapper() {
    }

    public static EnvironmentData toData(EnvironmentEntry entry) {
        EnvironmentData data = new EnvironmentData();
        data.setId(entry.getId() == null ? null : entry.getId().value());
        data.setProjectId(entry.getProjectId() == null ? null : entry.getProjectId().value());
        data.setItemKey(entry.getKey());
        data.setItemValue(entry.getValue());
        data.setTags(entry.getTags().isEmpty() ? null : String.join(",", entry.getTags()));
        data.setDescription(entry.getDescription());
        data.setCreatedAt(toDb(entry.getCreatedAt()));
        data.setUpdatedAt(toDb(entry.getUpdatedAt()));
        return data;
    }

    public static EnvironmentEntry toEntry(EnvironmentRow row) {
        EnvironmentScope scope = row.getProjectId() == null
                ? EnvironmentScope.GLOBAL
                : EnvironmentScope.PROJECT;
        ProjectId projectId = row.getProjectId() == null ? null : ProjectId.of(row.getProjectId());
        return EnvironmentEntry.restore(
                EnvironmentId.of(row.getId()),
                scope,
                projectId,
                row.getItemKey(),
                row.getItemValue(),
                parseTags(row.getTags()),
                row.getDescription(),
                toInstant(row.getCreatedAt()),
                toInstant(row.getUpdatedAt()));
    }

    private static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
