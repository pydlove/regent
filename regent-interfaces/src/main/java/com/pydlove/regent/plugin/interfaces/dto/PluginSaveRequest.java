package com.pydlove.regent.plugin.interfaces.dto;

import java.util.List;

/** 新增或更新插件请求。 */
public record PluginSaveRequest(
        String name,
        String description,
        List<String> tags,
        String filename,
        String content,
        Boolean enabled) {
}
