package com.pydlove.regent.plugin.domain.port;

import com.pydlove.regent.plugin.domain.model.BuiltinPluginDefinition;

import java.util.List;

/** 读取发行包内置插件定义的端口。 */
public interface BuiltinPluginCatalog {

    List<BuiltinPluginDefinition> loadAll();
}
