package com.pydlove.regent.plugin.interfaces.rest;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.plugin.application.command.PluginCommandService;
import com.pydlove.regent.plugin.application.command.SavePluginCommand;
import com.pydlove.regent.plugin.application.command.TaskPluginService;
import com.pydlove.regent.plugin.application.query.PluginQueryService;
import com.pydlove.regent.plugin.domain.model.Plugin;
import com.pydlove.regent.plugin.domain.model.PluginBinding;
import com.pydlove.regent.plugin.domain.model.PluginId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PluginControllerTest {

    private RecordingPluginCommand command;
    private RecordingPluginQuery query;
    private RecordingTaskPlugin taskPlugins;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        command = new RecordingPluginCommand();
        query = new RecordingPluginQuery();
        taskPlugins = new RecordingTaskPlugin();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PluginController(command, query),
                        new TaskPluginController(taskPlugins))
                .build();
    }

    @Test
    @DisplayName("创建插件返回版本详情并映射请求字段")
    void 创建插件() throws Exception {
        command.created = plugin();
        query.plugin = command.created;

        mockMvc.perform(post("/api/plugins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Kafka 读取组件","description":"复用消费者","tags":["Kafka"],
                                 "filename":"KafkaReader.java","content":"class KafkaReader {}","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.currentRevision").value(1))
                .andExpect(jsonPath("$.content").value("class KafkaReader {}"));

        assertEquals("Kafka 读取组件", command.save.name());
    }

    @Test
    @DisplayName("任务插件接口整体替换绑定并返回固定版本")
    void 绑定任务插件() throws Exception {
        taskPlugins.bindings = List.of(new PluginBinding(
                PluginId.of(11), "Kafka 读取组件", 2, "KafkaReader.java",
                "a".repeat(64), "content", true, Instant.parse("2026-10-17T00:00:00Z")));

        mockMvc.perform(put("/api/tasks/{id}/plugins", 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pluginIds\":[11]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pluginId").value(11))
                .andExpect(jsonPath("$[0].revision").value(2));

        assertEquals(TaskId.of(7), taskPlugins.taskId);
        assertEquals(List.of(PluginId.of(11)), taskPlugins.pluginIds);
    }

    @Test
    @DisplayName("列表接口不携带插件完整内容")
    void 列表不返回内容() throws Exception {
        query.plugin = plugin();
        query.plugins = List.of(query.plugin);

        mockMvc.perform(get("/api/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kafka 读取组件"))
                .andExpect(jsonPath("$[0].content").doesNotExist());
    }

    private static Plugin plugin() {
        Plugin plugin = Plugin.create(
                "Kafka 读取组件",
                "复用消费者",
                List.of("Kafka"),
                "KafkaReader.java",
                "class KafkaReader {}",
                true);
        plugin.assignIdentity(PluginId.of(11));
        return plugin;
    }

    private static final class RecordingPluginCommand extends PluginCommandService {

        private Plugin created;
        private SavePluginCommand save;

        private RecordingPluginCommand() {
            super(null, null);
        }

        @Override
        public Plugin create(SavePluginCommand command) {
            save = command;
            return created;
        }
    }

    private static final class RecordingPluginQuery extends PluginQueryService {

        private Plugin plugin;
        private List<Plugin> plugins = List.of();

        private RecordingPluginQuery() {
            super(null);
        }

        @Override
        public List<Plugin> list() {
            return plugins;
        }

        @Override
        public Plugin get(PluginId id) {
            return plugin;
        }
    }

    private static final class RecordingTaskPlugin extends TaskPluginService {

        private TaskId taskId;
        private List<PluginId> pluginIds;
        private List<PluginBinding> bindings = List.of();

        private RecordingTaskPlugin() {
            super(null, null, null, null);
        }

        @Override
        public List<PluginBinding> list(TaskId taskId) {
            return bindings;
        }

        @Override
        public List<PluginBinding> bind(TaskId taskId, List<PluginId> pluginIds) {
            this.taskId = taskId;
            this.pluginIds = pluginIds;
            return bindings;
        }
    }
}
