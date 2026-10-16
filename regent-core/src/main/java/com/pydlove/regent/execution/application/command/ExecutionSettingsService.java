package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.application.query.ExecutionSettingsView;
import com.pydlove.regent.execution.domain.model.ExecutionSettings;
import com.pydlove.regent.execution.domain.repository.ExecutionSettingsRepository;
import com.pydlove.regent.execution.domain.service.ExecutionConcurrencyGate;

/**
 * 平台级执行设置的读写入口。
 *
 * <p>读取时把持久化值同步到运行时闸门，保证配置文件、数据库与当前进程不会出现第三个上限。
 */
public class ExecutionSettingsService {

    private final ExecutionSettingsRepository repository;
    private final ExecutionConcurrencyGate gate;
    private final int defaultMaxConcurrentTasks;

    public ExecutionSettingsService(ExecutionSettingsRepository repository,
                                    ExecutionConcurrencyGate gate,
                                    int defaultMaxConcurrentTasks) {
        this.repository = repository;
        this.gate = gate;
        this.defaultMaxConcurrentTasks = new ExecutionSettings(defaultMaxConcurrentTasks).maxConcurrentTasks();
    }

    public ExecutionSettingsView current() {
        ExecutionSettings settings = repository.find()
                .orElseGet(() -> new ExecutionSettings(defaultMaxConcurrentTasks));
        gate.updateLimit(settings.maxConcurrentTasks());
        return view(settings);
    }

    public ExecutionSettingsView update(int maxConcurrentTasks) {
        ExecutionSettings settings = new ExecutionSettings(maxConcurrentTasks);
        repository.save(settings);
        gate.updateLimit(settings.maxConcurrentTasks());
        return view(settings);
    }

    private ExecutionSettingsView view(ExecutionSettings settings) {
        return new ExecutionSettingsView(settings.maxConcurrentTasks(), gate.activeCount());
    }
}
