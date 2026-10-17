package com.pydlove.regent.plugin.application.command;

import com.pydlove.regent.plugin.domain.model.Plugin;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.plugin.domain.repository.PluginRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

/** 插件写侧用例。 */
public class PluginCommandService {

    private final PluginRepository pluginRepository;
    private final TransactionRunner transactionRunner;

    public PluginCommandService(PluginRepository pluginRepository, TransactionRunner transactionRunner) {
        this.pluginRepository = pluginRepository;
        this.transactionRunner = transactionRunner;
    }

    public Plugin create(SavePluginCommand command) {
        return transactionRunner.inTransaction(() -> {
            Plugin plugin = Plugin.create(
                    command.name(),
                    command.description(),
                    command.tags(),
                    command.filename(),
                    command.content(),
                    command.enabled() == null || command.enabled());
            requireNameAvailable(plugin.getName(), null);
            pluginRepository.save(plugin);
            return plugin;
        });
    }

    public Plugin update(PluginId id, SavePluginCommand command) {
        return transactionRunner.inTransaction(() -> {
            Plugin plugin = pluginRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("插件不存在：" + id));
            String nextName = command.name() == null ? plugin.getName() : command.name();
            requireNameAvailable(nextName, id);
            plugin.update(
                    nextName,
                    command.description(),
                    command.tags(),
                    command.filename(),
                    command.content(),
                    command.enabled() == null ? plugin.isEnabled() : command.enabled());
            pluginRepository.save(plugin);
            return plugin;
        });
    }

    public Plugin setEnabled(PluginId id, boolean enabled) {
        return transactionRunner.inTransaction(() -> {
            Plugin plugin = pluginRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("插件不存在：" + id));
            plugin.changeEnabled(enabled);
            pluginRepository.save(plugin);
            return plugin;
        });
    }

    private void requireNameAvailable(String name, PluginId excludingId) {
        if (pluginRepository.existsByName(name, excludingId)) {
            throw new DomainException("已存在同名插件：" + name);
        }
    }
}
