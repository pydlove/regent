package com.pydlove.regent.project.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.UserId;
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
        data.setUserId(entry.getUserId() == null ? null : entry.getUserId().value());
        data.setProjectId(entry.getProjectId() == null ? null : entry.getProjectId().value());
        data.setItemKey(entry.getKey());
        data.setItemValue(entry.getValue());
        data.setTags(entry.getTags().isEmpty() ? null : String.join(",", entry.getTags()));
        data.setDescription(entry.getDescription());
        data.setSecret(entry.isSecret());
        data.setCreatedAt(toDb(entry.getCreatedAt()));
        data.setUpdatedAt(toDb(entry.getUpdatedAt()));
        return data;
    }

    public static EnvironmentEntry toEntry(EnvironmentRow row) {
        return toEntry(row, row.getProjectId() == null ? List.of() : List.of(row.getProjectId()));
    }

    public static EnvironmentEntry toEntry(EnvironmentRow row, List<Long> projectIds) {
        EnvironmentScope scope = projectIds.isEmpty()
                ? EnvironmentScope.GLOBAL
                : EnvironmentScope.PROJECT;
        return EnvironmentEntry.restore(
                EnvironmentId.of(row.getId()),
                row.getUserId() == null ? null : UserId.of(row.getUserId()),
                scope,
                projectIds.stream().map(ProjectId::of).toList(),
                row.getItemKey(),
                row.getItemValue(),
                parseTags(row.getTags()),
                row.getDescription(),
                Boolean.TRUE.equals(row.getSecret()),
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
