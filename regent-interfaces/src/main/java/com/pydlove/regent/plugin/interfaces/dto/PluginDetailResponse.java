package com.pydlove.regent.plugin.interfaces.dto;

import com.pydlove.regent.plugin.domain.model.Plugin;

import java.time.Instant;
import java.util.List;

/** 插件详情，包含当前版本的完整内容。 */
public record PluginDetailResponse(
        long id,
        String name,
        String description,
        List<String> tags,
        boolean builtin,
        boolean enabled,
        int currentRevision,
        String filename,
        String content,
        String contentHash,
        Instant createdAt,
        Instant updatedAt) {

    public static PluginDetailResponse from(Plugin plugin) {
        return new PluginDetailResponse(
                plugin.getId().value(),
                plugin.getName(),
                plugin.getDescription(),
                plugin.getTags(),
                plugin.isBuiltin(),
                plugin.isEnabled(),
                plugin.getCurrentVersion().revision(),
                plugin.getCurrentVersion().filename(),
                plugin.getCurrentVersion().content(),
                plugin.getCurrentVersion().contentHash(),
                plugin.getCreatedAt(),
                plugin.getUpdatedAt());
    }
}
