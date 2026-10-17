package com.pydlove.regent.plugin.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 任务与某个插件固定版本之间的绑定快照。 */
public record PluginBinding(
        PluginId pluginId,
        String pluginName,
        int revision,
        String filename,
        String contentHash,
        String content,
        boolean enabled,
        Instant boundAt) {

    public PluginBinding {
        Objects.requireNonNull(pluginId, "插件 ID 不能为空");
        pluginName = requireText(pluginName, "插件名称");
        if (revision <= 0) {
            throw new DomainException("插件版本必须为正数：" + revision);
        }
        filename = requireText(filename, "插件文件名");
        contentHash = requireText(contentHash, "插件内容摘要");
        content = requireText(content, "插件内容");
        boundAt = Objects.requireNonNull(boundAt, "绑定时间不能为空").truncatedTo(ChronoUnit.MILLIS);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }
}
