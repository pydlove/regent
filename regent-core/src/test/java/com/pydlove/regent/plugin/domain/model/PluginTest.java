package com.pydlove.regent.plugin.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginTest {

    @Test
    @DisplayName("新建插件生成内容摘要与第一版，标签去重")
    void createsFirstVersion() {
        Plugin plugin = Plugin.create(
                " Kafka 读取组件 ",
                " 复用已有消费者代码 ",
                List.of("Kafka", "中间件", "Kafka"),
                "KafkaReader.java",
                "class KafkaReader {}",
                true);

        assertEquals("Kafka 读取组件", plugin.getName());
        assertEquals("复用已有消费者代码", plugin.getDescription());
        assertEquals(List.of("Kafka", "中间件"), plugin.getTags());
        assertEquals(1, plugin.getCurrentVersion().revision());
        assertEquals("KafkaReader.java", plugin.getCurrentVersion().filename());
        assertEquals(64, plugin.getCurrentVersion().contentHash().length());
    }

    @Test
    @DisplayName("更新插件只递增版本，不改已有版本对象")
    void updateCreatesNextVersion() {
        Plugin plugin = Plugin.create("Kafka", null, List.of(), "reader.txt", "v1", true);
        PluginVersion first = plugin.getCurrentVersion();

        plugin.update("Kafka", "说明", List.of("消息"), "reader.txt", "v2", true);

        assertEquals(1, first.revision());
        assertEquals("v1", first.content());
        assertEquals(2, plugin.getCurrentVersion().revision());
        assertEquals("v2", plugin.getCurrentVersion().content());
        assertNotEquals(first.contentHash(), plugin.getCurrentVersion().contentHash());
    }

    @Test
    @DisplayName("停用的插件不能绑定新任务")
    void disabledPluginCannotBind() {
        Plugin plugin = Plugin.create("Kafka", null, List.of(), "reader.txt", "content", false);
        plugin.assignIdentity(PluginId.of(1));

        DomainException error = assertThrows(DomainException.class, () -> plugin.bind(Instant.now()));

        assertTrue(error.getMessage().contains("停用"));
    }

    @Test
    @DisplayName("插件文件名不能伪装成路径")
    void rejectsPathFilename() {
        assertThrows(DomainException.class, () -> Plugin.create(
                "Kafka", null, List.of(), "../reader.txt", "content", true));
    }

    @Test
    @DisplayName("内置插件同步保留启停状态，内容不变不升版本")
    void synchronizesBuiltinWithoutLosingState() {
        Plugin plugin = Plugin.createBuiltin(
                "builtin-probe",
                "内置探针",
                "第一版说明",
                List.of("通用"),
                "probe.md",
                "# 第一版",
                false);

        boolean metadataChanged = plugin.synchronizeBuiltin(
                "内置探针",
                "第二版说明",
                List.of("通用", "质量"),
                "probe.md",
                "# 第一版");

        assertTrue(metadataChanged);
        assertFalse(plugin.isEnabled());
        assertEquals(1, plugin.getCurrentVersion().revision());

        boolean contentChanged = plugin.synchronizeBuiltin(
                "内置探针",
                "第二版说明",
                List.of("通用", "质量"),
                "probe.md",
                "# 第二版");

        assertTrue(contentChanged);
        assertEquals(2, plugin.getCurrentVersion().revision());
        assertFalse(plugin.isEnabled());
        assertFalse(plugin.synchronizeBuiltin(
                "内置探针",
                "第二版说明",
                List.of("通用", "质量"),
                "probe.md",
                "# 第二版"));
    }

    @Test
    @DisplayName("内置插件不能走手工编辑入口")
    void rejectsManualUpdateOfBuiltin() {
        Plugin plugin = Plugin.createBuiltin(
                "builtin-probe", "内置探针", null, List.of(), "probe.md", "content", true);

        DomainException error = assertThrows(DomainException.class, () -> plugin.update(
                "内置探针", null, List.of(), "probe.md", "changed", true));

        assertTrue(error.getMessage().contains("内置插件"));
    }
}
