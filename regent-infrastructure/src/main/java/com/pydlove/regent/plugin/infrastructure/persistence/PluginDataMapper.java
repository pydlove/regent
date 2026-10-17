package com.pydlove.regent.plugin.infrastructure.persistence;

import com.pydlove.regent.plugin.domain.model.Plugin;
import com.pydlove.regent.plugin.domain.model.PluginBinding;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.plugin.domain.model.PluginVersion;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

/** 插件领域对象与 MyBatis 数据对象转换。 */
public final class PluginDataMapper {

    private PluginDataMapper() {
    }

    public static PluginData toData(Plugin plugin) {
        PluginData data = new PluginData();
        data.setId(plugin.getId() == null ? null : plugin.getId().value());
        data.setName(plugin.getName());
        data.setDescription(plugin.getDescription());
        data.setTags(plugin.getTags().isEmpty() ? null : String.join(",", plugin.getTags()));
        data.setEnabled(plugin.isEnabled());
        data.setCurrentRevision(plugin.getCurrentVersion().revision());
        data.setCreatedAt(toDb(plugin.getCreatedAt()));
        data.setUpdatedAt(toDb(plugin.getUpdatedAt()));
        return data;
    }

    public static PluginVersionRow toVersionRow(PluginId pluginId, PluginVersion version) {
        PluginVersionRow row = new PluginVersionRow();
        row.setPluginId(pluginId.value());
        row.setRevision(version.revision());
        row.setFilename(version.filename());
        row.setContent(version.content());
        row.setContentHash(version.contentHash());
        row.setCreatedAt(toDb(version.createdAt()));
        return row;
    }

    public static Plugin toPlugin(PluginRow row, PluginVersionRow version) {
        return Plugin.restore(
                PluginId.of(row.getId()),
                row.getName(),
                row.getDescription(),
                parseTags(row.getTags()),
                Boolean.TRUE.equals(row.getEnabled()),
                toVersion(version),
                toInstant(row.getCreatedAt()),
                toInstant(row.getUpdatedAt()));
    }

    public static PluginVersion toVersion(PluginVersionRow row) {
        return PluginVersion.restore(
                row.getRevision(),
                row.getFilename(),
                row.getContent(),
                row.getContentHash(),
                toInstant(row.getCreatedAt()));
    }

    public static PluginBinding toBinding(PluginBindingRow row) {
        return new PluginBinding(
                PluginId.of(row.getPluginId()),
                row.getPluginName(),
                row.getRevision(),
                row.getFilename(),
                row.getContentHash(),
                row.getContent(),
                Boolean.TRUE.equals(row.getEnabled()),
                toInstant(row.getBoundAt()));
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
