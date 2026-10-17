package com.pydlove.regent.plugin.infrastructure.builtin;

import com.pydlove.regent.plugin.domain.model.BuiltinPluginDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasspathBuiltinPluginCatalogTest {

    @Test
    @DisplayName("Maven 资源包含全部内置插件清单和正文")
    void loadsAllBundledPlugins() {
        List<BuiltinPluginDefinition> definitions = new ClasspathBuiltinPluginCatalog().loadAll();

        assertTrue(definitions.size() >= 9, "内置插件数量少于预期");
        assertEquals(definitions.size(), definitions.stream().map(BuiltinPluginDefinition::key)
                .collect(Collectors.toSet()).size());
        Set<String> expectedKeys = Set.of(
                "engineering-delivery-protocol",
                "change-safety-boundaries",
                "test-and-acceptance-loop",
                "frontend-apple-interaction",
                "java-layered-implementation",
                "database-migration-safety",
                "api-contract-quality",
                "kafka-remote-read",
                "zookeeper-remote-read");
        assertTrue(definitions.stream().map(BuiltinPluginDefinition::key).collect(Collectors.toSet())
                .containsAll(expectedKeys));
        assertTrue(definitions.stream().allMatch(definition ->
                definition.content().length() > 100 && definition.filename().endsWith(".md")));
        assertFalse(definitions.stream().anyMatch(definition -> definition.tags().isEmpty()));
    }

    @Test
    @DisplayName("远程只读插件包含连接契约和只读执行入口")
    void includesRemoteReadPluginContracts() {
        Map<String, BuiltinPluginDefinition> byKey = new ClasspathBuiltinPluginCatalog().loadAll().stream()
                .collect(Collectors.toMap(BuiltinPluginDefinition::key, Function.identity()));

        BuiltinPluginDefinition kafka = byKey.get("kafka-remote-read");
        assertNotNull(kafka, "缺少 Kafka 远程只读插件");
        assertTrue(kafka.content().contains("KAFKA_BOOTSTRAP_SERVERS"));
        assertTrue(kafka.content().contains("KAFKA_TOPICS"));
        assertTrue(kafka.content().contains("consumer.assign"));
        assertTrue(kafka.content().contains("enable_auto_commit"));
        assertTrue(kafka.content().contains("不提交 offset"));

        BuiltinPluginDefinition zookeeper = byKey.get("zookeeper-remote-read");
        assertNotNull(zookeeper, "缺少 ZooKeeper 远程只读插件");
        assertTrue(zookeeper.content().contains("ZOOKEEPER_CONNECT"));
        assertTrue(zookeeper.content().contains("ZOOKEEPER_PATH"));
        assertTrue(zookeeper.content().contains("client.get_children"));
        assertTrue(zookeeper.content().contains("不创建、修改、删除节点"));
    }
}
