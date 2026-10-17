package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.plugin.application.command.PluginCommandService;
import com.pydlove.regent.plugin.application.command.SavePluginCommand;
import com.pydlove.regent.plugin.application.command.TaskPluginService;
import com.pydlove.regent.plugin.domain.model.Plugin;
import com.pydlove.regent.plugin.domain.model.PluginBinding;
import com.pydlove.regent.plugin.domain.repository.PluginRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 插件、版本与任务绑定在真实 MySQL 上的往返验证。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PluginPersistenceIT {

    private static final long PROJECT_ID = 9997L;

    @Autowired
    private PluginCommandService pluginCommandService;

    @Autowired
    private PluginRepository pluginRepository;

    @Autowired
    private TaskPluginService taskPluginService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 准备探针项目() {
        jdbcTemplate.update("""
                INSERT INTO project (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'plugin_it_probe', '插件 IT 探针', '/tmp/plugin-it', 'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name), deleted = 0
                """, PROJECT_ID);
    }

    @Test
    @DisplayName("插件版本与任务固定版本绑定可以完整往返")
    void 插件与任务绑定往返() {
        Plugin plugin = pluginCommandService.create(new SavePluginCommand(
                "Kafka 读取组件",
                "复用已有消费者",
                List.of("Kafka", "中间件"),
                "KafkaReader.java",
                "class KafkaReader { void read() {} }",
                true));
        Task task = Task.manual(ProjectId.of(PROJECT_ID), "读取 Kafka", "读取 Kafka 消息", 0);
        taskRepository.save(task);

        taskPluginService.bind(task.getId(), List.of(plugin.getId()));
        Plugin upgraded = pluginCommandService.update(plugin.getId(), new SavePluginCommand(
                plugin.getName(),
                plugin.getDescription(),
                plugin.getTags(),
                "KafkaReader.java",
                "class KafkaReader { void readV2() {} }",
                true));

        List<PluginBinding> bindings = taskPluginService.list(task.getId());
        assertEquals(1, bindings.size());
        assertEquals(1, bindings.get(0).revision());
        assertTrue(bindings.get(0).content().contains("read()"));
        assertEquals(2, upgraded.getCurrentVersion().revision());
        assertEquals(2, pluginRepository.findById(plugin.getId()).orElseThrow()
                .getCurrentVersion().revision());

        pluginCommandService.setEnabled(plugin.getId(), false);

        assertFalse(taskPluginService.list(task.getId()).get(0).enabled());
    }
}
