package com.pydlove.regent.boot.config;

import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.plugin.application.command.PluginCommandService;
import com.pydlove.regent.plugin.application.command.TaskPluginService;
import com.pydlove.regent.plugin.application.query.PluginQueryService;
import com.pydlove.regent.plugin.domain.repository.PluginRepository;
import com.pydlove.regent.plugin.domain.repository.TaskPluginRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 插件上下文的显式装配。 */
@Configuration
public class PluginConfig {

    @Bean
    public PluginCommandService pluginCommandService(PluginRepository pluginRepository,
                                                     TransactionRunner transactionRunner) {
        return new PluginCommandService(pluginRepository, transactionRunner);
    }

    @Bean
    public PluginQueryService pluginQueryService(PluginRepository pluginRepository) {
        return new PluginQueryService(pluginRepository);
    }

    @Bean
    public TaskPluginService taskPluginService(TaskRepository taskRepository,
                                               PluginRepository pluginRepository,
                                               TaskPluginRepository taskPluginRepository,
                                               TransactionRunner transactionRunner) {
        return new TaskPluginService(
                taskRepository, pluginRepository, taskPluginRepository, transactionRunner);
    }
}
