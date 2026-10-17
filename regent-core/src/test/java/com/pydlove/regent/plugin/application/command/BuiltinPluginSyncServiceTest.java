package com.pydlove.regent.plugin.application.command;

import com.pydlove.regent.plugin.domain.model.BuiltinPluginDefinition;
import com.pydlove.regent.plugin.domain.model.Plugin;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.plugin.domain.model.PluginVersion;
import com.pydlove.regent.plugin.domain.port.BuiltinPluginCatalog;
import com.pydlove.regent.plugin.domain.repository.PluginRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinPluginSyncServiceTest {

    @Test
    @DisplayName("首次同步创建插件，重复同步保持幂等")
    void createsAndThenKeepsIdempotent() {
        InMemoryPluginRepository repository = new InMemoryPluginRepository();
        BuiltinPluginSyncService service = service(repository, List.of(definition("v1")));

        BuiltinPluginSyncResult first = service.synchronize();
        BuiltinPluginSyncResult second = service.synchronize();

        assertEquals(1, first.created());
        assertEquals(0, first.updated());
        assertEquals(0, second.created());
        assertEquals(0, second.updated());
        assertEquals(1, second.unchanged());
        assertTrue(repository.byKey.get("builtin-probe").isBuiltin());
    }

    @Test
    @DisplayName("发行包更新内容和元数据时升版本，并保留用户停用状态")
    void updatesBuiltinButPreservesEnabledState() {
        InMemoryPluginRepository repository = new InMemoryPluginRepository();
        BuiltinPluginSyncService service = service(repository, List.of(definition("v1")));
        service.synchronize();
        Plugin existing = repository.byKey.get("builtin-probe");
        existing.changeEnabled(false);

        BuiltinPluginSyncResult result = service(repository, List.of(definition("v2"))).synchronize();

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        assertEquals(2, existing.getCurrentVersion().revision());
        assertFalse(existing.isEnabled());
    }

    @Test
    @DisplayName("空清单直接失败，避免打包遗漏被静默吞掉")
    void rejectsEmptyCatalog() {
        BuiltinPluginSyncService service = service(new InMemoryPluginRepository(), List.of());

        DomainException error = assertThrows(DomainException.class, service::synchronize);

        assertTrue(error.getMessage().contains("没有发现内置插件"));
    }

    private static BuiltinPluginSyncService service(PluginRepository repository,
                                                     List<BuiltinPluginDefinition> definitions) {
        BuiltinPluginCatalog catalog = () -> definitions;
        return new BuiltinPluginSyncService(repository, catalog, new DirectTransactionRunner());
    }

    private static BuiltinPluginDefinition definition(String content) {
        return new BuiltinPluginDefinition(
                "builtin-probe",
                "内置探针",
                "同步测试",
                List.of("测试"),
                "probe.md",
                content,
                true);
    }

    private static final class DirectTransactionRunner
            implements com.pydlove.regent.shared.domain.TransactionRunner {

        @Override
        public <T> T inTransaction(java.util.function.Supplier<T> action) {
            return action.get();
        }
    }

    private static final class InMemoryPluginRepository implements PluginRepository {

        private final Map<String, Plugin> byKey = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public Optional<Plugin> findById(PluginId id) {
            return byKey.values().stream()
                    .filter(plugin -> plugin.getId().equals(id))
                    .findFirst();
        }

        @Override
        public Optional<Plugin> findByIdForUpdate(PluginId id) {
            return findById(id);
        }

        @Override
        public Optional<Plugin> findByBuiltinKeyForUpdate(String builtinKey) {
            return Optional.ofNullable(byKey.get(builtinKey));
        }

        @Override
        public Optional<PluginVersion> findVersion(PluginId id, int revision) {
            return findById(id)
                    .filter(plugin -> plugin.getCurrentVersion().revision() == revision)
                    .map(Plugin::getCurrentVersion);
        }

        @Override
        public List<Plugin> findAll() {
            return new ArrayList<>(byKey.values());
        }

        @Override
        public boolean existsByName(String name, PluginId excludingId) {
            return byKey.values().stream().anyMatch(plugin ->
                    plugin.getName().equals(name) && !plugin.getId().equals(excludingId));
        }

        @Override
        public void save(Plugin plugin) {
            if (plugin.getId() == null) {
                plugin.assignIdentity(PluginId.of(sequence.incrementAndGet()));
            }
            if (plugin.getBuiltinKey() != null) {
                byKey.put(plugin.getBuiltinKey(), plugin);
            }
        }
    }
}
