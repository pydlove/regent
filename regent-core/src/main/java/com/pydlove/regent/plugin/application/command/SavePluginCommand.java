package com.pydlove.regent.plugin.application.command;

import java.util.List;

/** 新增或更新插件的命令。 */
public record SavePluginCommand(
        String name,
        String description,
        List<String> tags,
        String filename,
        String content,
        Boolean enabled) {
}
