package com.pydlove.regent.plugin.interfaces.dto;

import java.util.List;

/** 整体替换任务插件绑定；空数组表示清空。 */
public record BindTaskPluginsRequest(List<Long> pluginIds) {
}
