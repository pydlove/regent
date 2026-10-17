package com.pydlove.regent.plugin.interfaces.dto;

import com.pydlove.regent.plugin.domain.model.Plugin;

import java.time.Instant;
import java.util.List;

/** 插件列表项，不携带完整内容。 */
public record PluginSummaryResponse(
        long id,
        String name,
        String description,
        List<String> tags,
        boolean enabled,
        int currentRevision,
        String filename,
        String contentHash,
        Instant createdAt,
        Instant updatedAt) {

    public static PluginSummaryResponse from(Plugin plugin) {
        return new PluginSummaryResponse(
                plugin.getId().value(),
                plugin.getName(),
                plugin.getDescription(),
                plugin.getTags(),
                plugin.isEnabled(),
                plugin.getCurrentVersion().revision(),
                plugin.getCurrentVersion().filename(),
                plugin.getCurrentVersion().contentHash(),
                plugin.getCreatedAt(),
                plugin.getUpdatedAt());
    }
}
