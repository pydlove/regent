package com.pydlove.regent.plugin.application.query;

import com.pydlove.regent.plugin.domain.model.Plugin;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.plugin.domain.repository.PluginRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;

/** 插件库读侧。 */
public class PluginQueryService {

    private final PluginRepository pluginRepository;

    public PluginQueryService(PluginRepository pluginRepository) {
        this.pluginRepository = pluginRepository;
    }

    public List<Plugin> list() {
        return pluginRepository.findAll();
    }

    public Plugin get(PluginId id) {
        return pluginRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("插件不存在：" + id));
    }
}
