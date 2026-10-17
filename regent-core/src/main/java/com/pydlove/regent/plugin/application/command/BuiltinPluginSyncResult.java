package com.pydlove.regent.plugin.application.command;

/** 一次内置插件启动同步的统计。 */
public record BuiltinPluginSyncResult(int discovered, int created, int updated, int unchanged) {
}
