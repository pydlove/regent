package com.pydlove.regent.plugin.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 插件强类型 ID。 */
public record PluginId(long value) {

    public PluginId {
        if (value <= 0) {
            throw new DomainException("插件 ID 必须为正数：" + value);
        }
    }

    public static PluginId of(long value) {
        return new PluginId(value);
    }
}
