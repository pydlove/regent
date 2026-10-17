package com.pydlove.regent.plugin.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.plugin.domain.model.Plugin;
import com.pydlove.regent.plugin.domain.model.PluginBinding;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.plugin.domain.model.PluginVersion;
import com.pydlove.regent.plugin.domain.repository.PluginRepository;
import com.pydlove.regent.plugin.domain.repository.TaskPluginRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPluginServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);
    private static final TaskId TASK = TaskId.of(10L);

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final Plugins plugins = new Plugins();
    private final Bindings bindings = new Bindings();
    private TaskPluginService service;

    @BeforeEach
    void setUp() {
        service = new TaskPluginService(
                tasks, plugins, bindings, new ExecutionFakes.Transactions());
    }

    @Test
    @DisplayName("绑定保存当时版本，插件升级不会改写已有任务")
    void bindingPinsCurrentVersion() {
        Plugin plugin = plugin("Kafka", "v1");
        tasks.add(task(TaskStatus.PENDING));

        List<PluginBinding> bound = service.bind(TASK, List.of(plugin.getId()));
        plugin.update("Kafka", null, List.of(), "reader.txt", "v2", true);

        assertEquals(1, bound.get(0).revision());
        assertEquals("v1", bound.get(0).content());
        assertEquals(1, service.list(TASK).get(0).revision());
        assertEquals("v1", service.resolve(TASK).plugins().get(0).content());
    }

    @Test
    @DisplayName("执行中、阻塞中和已完成任务禁止改绑")
    void rejectsBindingAfterExecutionStarts() {
        Plugin plugin = plugin("Kafka", "v1");
        tasks.add(task(TaskStatus.RUNNING));

        DomainException error = assertThrows(DomainException.class,
                () -> service.bind(TASK, List.of(plugin.getId())));

        assertTrue(error.getMessage().contains("开始执行后"));
    }

    @Test
    @DisplayName("停用插件不能再绑定，已绑定后停用则执行前失败关闭")
    void disabledPluginFailsClosed() {
        Plugin plugin = plugin("Kafka", "v1");
        tasks.add(task(TaskStatus.PENDING));
        service.bind(TASK, List.of(plugin.getId()));

        plugin.changeEnabled(false);

        DomainException error = assertThrows(DomainException.class, () -> service.resolve(TASK));
        assertTrue(error.getMessage().contains("停用"));
    }

    @Test
    @DisplayName("空集合表示清空绑定")
    void emptySelectionClearsBindings() {
        Plugin plugin = plugin("Kafka", "v1");
        tasks.add(task(TaskStatus.PENDING));
        service.bind(TASK, List.of(plugin.getId()));

        service.bind(TASK, List.of());

        assertTrue(service.list(TASK).isEmpty());
    }

    private Plugin plugin(String name, String content) {
        Plugin plugin = Plugin.create(name, null, List.of(), "reader.txt", content, true);
        plugins.save(plugin);
        return plugin;
    }

    private static Task task(TaskStatus status) {
        return ExecutionFakes.task(TASK.value(), PROJECT, status);
    }

    private static final class Plugins implements PluginRepository {

        private final Map<Long, Plugin> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<Plugin> findById(PluginId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<Plugin> findByIdForUpdate(PluginId id) {
            return findById(id);
        }

        @Override
        public Optional<Plugin> findByBuiltinKeyForUpdate(String builtinKey) {
            return store.values().stream()
                    .filter(plugin -> builtinKey.equals(plugin.getBuiltinKey()))
                    .findFirst();
        }

        @Override
        public Optional<PluginVersion> findVersion(PluginId id, int revision) {
            return findById(id)
                    .map(Plugin::getCurrentVersion)
                    .filter(version -> version.revision() == revision);
        }

        @Override
        public List<Plugin> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public boolean existsByName(String name, PluginId excludingId) {
            return store.values().stream()
                    .filter(plugin -> excludingId == null || !plugin.getId().equals(excludingId))
                    .anyMatch(plugin -> plugin.getName().equals(name));
        }

        @Override
        public void save(Plugin plugin) {
            if (plugin.getId() == null) {
                plugin.assignIdentity(PluginId.of(++sequence));
            }
            store.put(plugin.getId().value(), plugin);
        }
    }

    private static final class Bindings implements TaskPluginRepository {

        private final Map<TaskId, List<PluginBinding>> store = new LinkedHashMap<>();

        @Override
        public List<PluginBinding> findByTask(TaskId taskId) {
            return List.copyOf(store.getOrDefault(taskId, List.of()));
        }

        @Override
        public void replace(TaskId taskId, List<PluginBinding> bindings) {
            store.put(taskId, new ArrayList<>(bindings));
        }
    }
}
