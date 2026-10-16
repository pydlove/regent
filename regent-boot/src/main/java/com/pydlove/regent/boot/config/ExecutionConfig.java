package com.pydlove.regent.boot.config;

import com.pydlove.regent.bug.domain.repository.BugRepository;
import com.pydlove.regent.execution.application.command.AgentRunService;
import com.pydlove.regent.execution.application.command.ExecutionSettingsService;
import com.pydlove.regent.execution.application.command.RunSettlementService;
import com.pydlove.regent.execution.application.command.TaskAttachmentService;
import com.pydlove.regent.execution.application.command.TaskDispatchService;
import com.pydlove.regent.execution.application.command.TaskRunService;
import com.pydlove.regent.execution.application.query.AgentContractService;
import com.pydlove.regent.execution.domain.model.ExecutionSettings;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.execution.domain.port.ExecutorContract;
import com.pydlove.regent.execution.domain.port.BranchManager;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.execution.domain.repository.TaskAttachmentRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.execution.domain.repository.ExecutionSettingsRepository;
import com.pydlove.regent.execution.domain.service.ExecutionConcurrencyGate;
import com.pydlove.regent.execution.infrastructure.agent.ClasspathExecutorContract;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.infrastructure.attachment.LocalAttachmentStorage;
import com.pydlove.regent.execution.infrastructure.codex.CodexCliAdapter;
import com.pydlove.regent.execution.infrastructure.codex.SchemaFileProvider;
import com.pydlove.regent.execution.infrastructure.git.GitBranchManager;
import com.pydlove.regent.governance.application.command.DecisionRaiseService;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行链路的装配：执行器、分支管理、调度与线程池。
 *
 * <p>进程级参数（超时、要不要开分支、并发默认值）来自 {@code application.yml}；
 * 运行时可编辑的执行设置由数据库保存。每个开关的含义与默认值都在这里写明白，
 * 不把「配置文件只是首次默认值」这层语义藏到注释之外。
 */
@Configuration
public class ExecutionConfig {

    /**
     * 出参契约的落盘目录。{@code codex exec --output-schema} 只收文件路径，所以必须落一次盘；
     * 落在运行时目录而不是仓库里，理由与日志、pid 相同：运行时产物不进版本库。
     */
    @Bean
    public SchemaFileProvider schemaFileProvider(@Value("${regent.home}") String home) {
        return new SchemaFileProvider(Path.of(home, "schema"));
    }

    @Bean
    public AgentExecutor agentExecutor(@Value("${regent.execution.codex-command}") String command,
                                       @Value("${regent.home}") String home,
                                       SchemaFileProvider schemaFileProvider) {
        return new CodexCliAdapter(command, Path.of(home, "runs"), schemaFileProvider);
    }

    @Bean
    public BranchManager branchManager(@Value("${regent.execution.branch-enabled:true}") boolean enabled) {
        return new GitBranchManager(enabled);
    }

    /**
     * 执行线程池。
     *
     * <p>线程池本身不再写死并发数，真正的闸门是 {@link ExecutionConcurrencyGate}。
     * 这样界面调大或调小上限时不需要重建线程池；同时仍由闸门保证最多只有配置数量的任务
     * 真正开始执行。线程按需创建、空闲后自然回收。
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService executionWorkers() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "regent-run-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newCachedThreadPool(factory);
    }

    /**
     * 数据库未保存执行设置时，采用配置文件的默认值；保存后由
     * {@link ExecutionSettingsService} 以数据库值为准，并在运行时更新闸门。
     */
    @Bean
    public ExecutionConcurrencyGate executionConcurrencyGate(
            ExecutionSettingsRepository executionSettingsRepository,
            @Value("${regent.execution.max-concurrency:1}") int defaultMaxConcurrency) {
        int maxConcurrentTasks = executionSettingsRepository.find()
                .orElseGet(() -> new ExecutionSettings(defaultMaxConcurrency))
                .maxConcurrentTasks();
        return new ExecutionConcurrencyGate(maxConcurrentTasks);
    }

    @Bean
    public ExecutionSettingsService executionSettingsService(
            ExecutionSettingsRepository executionSettingsRepository,
            ExecutionConcurrencyGate executionConcurrencyGate,
            @Value("${regent.execution.max-concurrency:1}") int defaultMaxConcurrency) {
        return new ExecutionSettingsService(
                executionSettingsRepository, executionConcurrencyGate, defaultMaxConcurrency);
    }

    /**
     * 附件存储。落在运行时目录下（{@code regent.home}/attachments），与日志、schema、runs 同级 ——
     * 图片属于运行时产物，不进仓库，也不进任务工作区（工作区是项目的代码，git status 必须干净）。
     */
    @Bean
    public AttachmentStorage attachmentStorage(@Value("${regent.attachments.dir}") String dir) {
        return new LocalAttachmentStorage(Path.of(dir));
    }

    @Bean
    public TaskAttachmentService taskAttachmentService(TaskRepository taskRepository,
                                                       TaskAttachmentRepository attachmentRepository,
                                                       AttachmentStorage attachmentStorage,
                                                       @Value("${regent.attachments.max-bytes}") long maxBytes) {
        return new TaskAttachmentService(taskRepository, attachmentRepository, attachmentStorage, maxBytes);
    }

    /**
     * 出参与契约的文本提供者。内部执行器与外部执行者读到的是同一份 —— 这正是放一个 bean 的理由。
     */
    @Bean
    public ExecutorContract executorContract(SchemaFileProvider schemaFileProvider) {
        return new ClasspathExecutorContract(schemaFileProvider);
    }

    /**
     * 一次尝试的编排水：开局说什么、收场怎么算。两条执行路径（内部进程 / 外部 HTTP）共用它，
     * 治理只允许有一份实现。
     */
    @Bean
    public RunSettlementService runSettlementService(TaskRepository taskRepository,
                                                     DecisionRepository decisionRepository,
                                                     DecisionRaiseService decisionRaiseService,
                                                     BugRepository bugRepository) {
        return new RunSettlementService(
                taskRepository, decisionRepository, decisionRaiseService, bugRepository);
    }

    @Bean
    public TaskRunService taskRunService(TaskRepository taskRepository,
                                         TaskRunRepository taskRunRepository,
                                         RunEventRepository runEventRepository,
                                         TaskAttachmentRepository attachmentRepository,
                                         ProjectProfileRepository projectProfileRepository,
                                         EnvironmentEntryRepository environmentEntryRepository,
                                         RunSettlementService runSettlementService,
                                         AgentExecutor agentExecutor,
                                         @Value("${regent.execution.timeout:PT30M}") Duration timeout) {
        return new TaskRunService(taskRepository, taskRunRepository, runEventRepository, attachmentRepository,
                projectProfileRepository, environmentEntryRepository, runSettlementService, agentExecutor, timeout);
    }

    /**
     * 外部执行者：认领 / 心跳 / 上报 / 失联回收。
     *
     * <p>租约默认 900 秒，与 {@code regent.execution.timeout} 同量级：一次真实的执行是几十分钟的事，
     * 租约太短会把正在干活的执行者判成失联，太长则用户关掉工具之后工作区被白占半小时。
     */
    @Bean
    public AgentRunService agentRunService(TaskRepository taskRepository,
                                           TaskRunRepository taskRunRepository,
                                           RunEventRepository runEventRepository,
                                           TaskAttachmentRepository attachmentRepository,
                                           ProjectProfileRepository projectProfileRepository,
                                           EnvironmentEntryRepository environmentEntryRepository,
                                           DecisionRepository decisionRepository,
                                           BranchManager branchManager,
                                           RunSettlementService runSettlementService,
                                           ExecutorContract executorContract,
                                           ExecutionConcurrencyGate executionConcurrencyGate,
                                           @Value("${regent.agent.lease-seconds:900}") long leaseSeconds,
                                           @Value("${regent.agent.contract-url:http://127.0.0.1:8787/api/agent/contract}")
                                           String contractUrl) {
        return new AgentRunService(taskRepository, taskRunRepository, runEventRepository, attachmentRepository,
                projectProfileRepository, environmentEntryRepository, decisionRepository, branchManager, runSettlementService,
                executorContract, executionConcurrencyGate, Duration.ofSeconds(leaseSeconds), contractUrl);
    }

    @Bean
    public AgentContractService agentContractService(ExecutorContract executorContract) {
        return new AgentContractService(executorContract);
    }

    @Bean
    public TaskDispatchService taskDispatchService(TaskRepository taskRepository,
                                                   ProjectProfileRepository projectProfileRepository,
                                                   BranchManager branchManager,
                                                   TaskRunService taskRunService,
                                                   ExecutorService executionWorkers,
                                                   ExecutionConcurrencyGate executionConcurrencyGate,
                                                   TransactionRunner transactionRunner) {
        return new TaskDispatchService(taskRepository, projectProfileRepository, branchManager,
                taskRunService, executionWorkers, executionConcurrencyGate, transactionRunner);
    }
}
