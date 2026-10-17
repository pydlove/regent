package com.pydlove.regent.plugin.application.command;

import com.pydlove.regent.plugin.domain.model.BuiltinPluginDefinition;
import com.pydlove.regent.plugin.domain.model.Plugin;
import com.pydlove.regent.plugin.domain.port.BuiltinPluginCatalog;
import com.pydlove.regent.plugin.domain.repository.PluginRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 启动时把发行包内的插件幂等同步到插件库。 */
public class BuiltinPluginSyncService {

    private final PluginRepository pluginRepository;
    private final BuiltinPluginCatalog builtinPluginCatalog;
    private final TransactionRunner transactionRunner;

    public BuiltinPluginSyncService(PluginRepository pluginRepository,
                                    BuiltinPluginCatalog builtinPluginCatalog,
                                    TransactionRunner transactionRunner) {
        this.pluginRepository = pluginRepository;
        this.builtinPluginCatalog = builtinPluginCatalog;
        this.transactionRunner = transactionRunner;
    }

    public BuiltinPluginSyncResult synchronize() {
        List<BuiltinPluginDefinition> definitions = builtinPluginCatalog.loadAll();
        if (definitions.isEmpty()) {
            throw new DomainException("发行包中没有发现内置插件，请检查 plugins 目录是否打进 JAR");
        }
        return transactionRunner.inTransaction(() -> synchronizeAll(definitions));
    }

    private BuiltinPluginSyncResult synchronizeAll(List<BuiltinPluginDefinition> definitions) {
        Set<String> keys = new HashSet<>();
        Set<String> names = new HashSet<>();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (BuiltinPluginDefinition definition : definitions) {
            if (!keys.add(definition.key())) {
                throw new DomainException("内置插件标识重复：" + definition.key());
            }
            if (!names.add(definition.name())) {
                throw new DomainException("内置插件名称重复：" + definition.name());
            }

            Plugin existing = pluginRepository.findByBuiltinKeyForUpdate(definition.key()).orElse(null);
            if (existing == null) {
                if (pluginRepository.existsByName(definition.name(), null)) {
                    throw new DomainException("内置插件名称已被手工插件占用：" + definition.name());
                }
                pluginRepository.save(Plugin.createBuiltin(
                        definition.key(),
                        definition.name(),
                        definition.description(),
                        definition.tags(),
                        definition.filename(),
                        definition.content(),
                        definition.defaultEnabled()));
                created++;
                continue;
            }

            if (existing.synchronizeBuiltin(
                    definition.name(),
                    definition.description(),
                    definition.tags(),
                    definition.filename(),
                    definition.content())) {
                pluginRepository.save(existing);
                updated++;
            } else {
                unchanged++;
            }
        }
        return new BuiltinPluginSyncResult(definitions.size(), created, updated, unchanged);
    }
}
