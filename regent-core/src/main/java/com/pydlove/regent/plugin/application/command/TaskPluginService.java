package com.pydlove.regent.plugin.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.plugin.application.query.PluginContext;
import com.pydlove.regent.plugin.domain.model.Plugin;
import com.pydlove.regent.plugin.domain.model.PluginBinding;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.plugin.domain.repository.PluginRepository;
import com.pydlove.regent.plugin.domain.repository.TaskPluginRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 任务插件绑定的读写与执行上下文解析。 */
public class TaskPluginService {

    public static final int MAX_PLUGINS_PER_TASK = 8;

    private final TaskRepository taskRepository;
    private final PluginRepository pluginRepository;
    private final TaskPluginRepository taskPluginRepository;
    private final TransactionRunner transactionRunner;

    public TaskPluginService(TaskRepository taskRepository,
                             PluginRepository pluginRepository,
                             TaskPluginRepository taskPluginRepository,
                             TransactionRunner transactionRunner) {
        this.taskRepository = taskRepository;
        this.pluginRepository = pluginRepository;
        this.taskPluginRepository = taskPluginRepository;
        this.transactionRunner = transactionRunner;
    }

    public List<PluginBinding> list(TaskId taskId) {
        requireTask(taskId);
        return taskPluginRepository.findByTask(taskId);
    }

    public PluginContext resolve(TaskId taskId) {
        List<PluginBinding> bindings = taskPluginRepository.findByTask(taskId);
        for (PluginBinding binding : bindings) {
            Plugin current = pluginRepository.findById(binding.pluginId())
                    .orElseThrow(() -> new EntityNotFoundException("插件不存在：" + binding.pluginId()));
            if (!current.isEnabled()) {
                throw new DomainException("任务绑定的插件已停用，请先移除或换绑：" + current.getName());
            }
        }
        return PluginContext.from(bindings);
    }

    public List<PluginBinding> bind(TaskId taskId, List<PluginId> pluginIds) {
        List<PluginId> unique = normalizeIds(pluginIds);
        return transactionRunner.inTransaction(() -> {
            Task task = taskRepository.findByIdForUpdate(taskId)
                    .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
            if (task.getStatus() == TaskStatus.RUNNING
                    || task.getStatus() == TaskStatus.BLOCKED
                    || task.getStatus() == TaskStatus.DONE) {
                throw new DomainException("任务开始执行后不能再修改插件绑定：" + task.getStatus());
            }
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            List<PluginBinding> bindings = new ArrayList<>();
            for (PluginId pluginId : unique) {
                Plugin plugin = pluginRepository.findById(pluginId)
                        .orElseThrow(() -> new EntityNotFoundException("插件不存在：" + pluginId));
                bindings.add(plugin.bind(now));
            }
            taskPluginRepository.replace(taskId, bindings);
            return List.copyOf(bindings);
        });
    }

    private Task requireTask(TaskId taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
    }

    private static List<PluginId> normalizeIds(List<PluginId> pluginIds) {
        if (pluginIds == null || pluginIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<PluginId> unique = new LinkedHashSet<>(pluginIds);
        if (unique.contains(null)) {
            throw new DomainException("插件 ID 不能为空");
        }
        if (unique.size() > MAX_PLUGINS_PER_TASK) {
            throw new DomainException("一个任务最多绑定 " + MAX_PLUGINS_PER_TASK + " 个插件");
        }
        return List.copyOf(unique);
    }
}
