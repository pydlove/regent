package com.pydlove.regent.plugin.interfaces.dto;

import com.pydlove.regent.plugin.domain.model.PluginBinding;

import java.time.Instant;

/** 任务当前绑定的插件版本。 */
public record TaskPluginResponse(
        long pluginId,
        String name,
        int revision,
        String filename,
        String contentHash,
        boolean enabled,
        Instant boundAt) {

    public static TaskPluginResponse from(PluginBinding binding) {
        return new TaskPluginResponse(
                binding.pluginId().value(),
                binding.pluginName(),
                binding.revision(),
                binding.filename(),
                binding.contentHash(),
                binding.enabled(),
                binding.boundAt());
    }
}
