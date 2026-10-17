package com.pydlove.regent.boot.config;

import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.plugin.application.command.BuiltinPluginSyncResult;
import com.pydlove.regent.plugin.application.command.BuiltinPluginSyncService;
import com.pydlove.regent.plugin.application.command.PluginCommandService;
import com.pydlove.regent.plugin.application.command.TaskPluginService;
import com.pydlove.regent.plugin.application.query.PluginQueryService;
import com.pydlove.regent.plugin.domain.port.BuiltinPluginCatalog;
import com.pydlove.regent.plugin.domain.repository.PluginRepository;
import com.pydlove.regent.plugin.domain.repository.TaskPluginRepository;
import com.pydlove.regent.plugin.infrastructure.builtin.ClasspathBuiltinPluginCatalog;
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 插件上下文的显式装配。 */
@Configuration
public class PluginConfig {

    private static final Logger log = LoggerFactory.getLogger(PluginConfig.class);

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

    @Bean
    public BuiltinPluginCatalog builtinPluginCatalog() {
        return new ClasspathBuiltinPluginCatalog();
    }

    @Bean
    public BuiltinPluginSyncService builtinPluginSyncService(PluginRepository pluginRepository,
                                                             BuiltinPluginCatalog builtinPluginCatalog,
                                                             TransactionRunner transactionRunner) {
        return new BuiltinPluginSyncService(
                pluginRepository, builtinPluginCatalog, transactionRunner);
    }

    /**
     * 内置插件只在启动时同步一次。清单或正文解析失败会让应用启动失败，
     * 不静默跳过：插件缺一半比明确报错更难排查。
     */
    @Bean
    public ApplicationRunner builtinPluginRunner(BuiltinPluginSyncService syncService) {
        return args -> {
            BuiltinPluginSyncResult result = syncService.synchronize();
            log.info("内置插件同步完成：发现 {}，新增 {}，更新 {}，未变 {}",
                    result.discovered(), result.created(), result.updated(), result.unchanged());
        };
    }
}
