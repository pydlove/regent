package com.pydlove.regent.plugin.application.query;

import com.pydlove.regent.plugin.domain.model.PluginBinding;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginContextTest {

    @Test
    @DisplayName("插件内容进入指令，并明确要求优先复用而不是重写")
    void rendersReusablePluginSection() {
        PluginContext context = new PluginContext(List.of(binding(
                "Kafka 读取组件", 2, "class KafkaReader { void read() {} }", true)));

        String prompt = context.promptSection();

        assertTrue(prompt.contains("优先阅读并直接复用，不要重新生成已有实现"));
        assertTrue(prompt.contains("Kafka 读取组件"));
        assertTrue(prompt.contains("v2"));
        assertTrue(prompt.contains("class KafkaReader"));
        assertTrue(context.auditPayload().contains("\"revision\":2"));
        assertTrue(!context.auditPayload().contains("class KafkaReader"),
                "审计事件不该重复落整份内容");
    }

    @Test
    @DisplayName("停用插件无法形成执行上下文")
    void rejectsDisabledPlugin() {
        assertThrows(DomainException.class,
                () -> new PluginContext(List.of(binding("Kafka", 1, "code", false))));
    }

    private static PluginBinding binding(String name, int revision, String content, boolean enabled) {
        return new PluginBinding(
                PluginId.of(1),
                name,
                revision,
                "reader.txt",
                "a".repeat(64),
                content,
                enabled,
                Instant.parse("2026-10-17T00:00:00Z"));
    }
}
