package com.pydlove.regent.boot.config;

import com.pydlove.regent.execution.application.command.TaskAttachmentService;
import com.pydlove.regent.execution.application.command.TaskDispatchService;
import com.pydlove.regent.execution.application.command.TaskRunService;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.execution.domain.port.AttachmentStorage;
import com.pydlove.regent.execution.domain.port.BranchManager;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.execution.domain.repository.TaskAttachmentRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.execution.infrastructure.attachment.LocalAttachmentStorage;
import com.pydlove.regent.execution.infrastructure.codex.CodexCliAdapter;
import com.pydlove.regent.execution.infrastructure.codex.SchemaFileProvider;
import com.pydlove.regent.execution.infrastructure.git.GitBranchManager;
import com.pydlove.regent.governance.application.command.DecisionRaiseService;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
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
 * <p>这里也是「开关写在配置里、语义写在代码里」那条分界线的落点：
 * 开关（并发数、超时、要不要开分支）都在 {@code application.yml} 的 {@code regent.execution.*} 下，
 * 但每个开关的含义与默认值都在这个类里写明白，而不是散在注释里让人猜。
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
     * <p>默认单线程，这是刻意的：一条执行链路会独占整个工作区（切分支、改文件）,
     * 同一项目并行跑两个任务会互相覆盖对方的改动。要放开并发，前提是先做到按项目隔离工作区
     * （clone 到独立目录），而不是把这里的数字调大。
     *
     * <p>队列不设上限：任务在执行之前就已经被认领（状态写成 RUNNING），
     * 队列拒收会让它变成一条「已认领但没人跑」的悬挂记录。真正的闸门在调度器那边 ——
     * 一次只认领有限个数。
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService executionWorkers(
            @Value("${regent.execution.max-concurrency:1}") int concurrency) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "regent-run-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(Math.max(1, concurrency), factory);
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

    @Bean
    public TaskRunService taskRunService(TaskRepository taskRepository,
                                         TaskRunRepository taskRunRepository,
                                         RunEventRepository runEventRepository,
                                         TaskAttachmentRepository attachmentRepository,
                                         ProjectProfileRepository projectProfileRepository,
                                         DecisionRepository decisionRepository,
                                         DecisionRaiseService decisionRaiseService,
                                         AgentExecutor agentExecutor,
                                         @Value("${regent.execution.timeout:PT30M}") Duration timeout) {
        return new TaskRunService(taskRepository, taskRunRepository, runEventRepository, attachmentRepository,
                projectProfileRepository, decisionRepository, decisionRaiseService, agentExecutor, timeout);
    }

    @Bean
    public TaskDispatchService taskDispatchService(TaskRepository taskRepository,
                                                   ProjectProfileRepository projectProfileRepository,
                                                   BranchManager branchManager,
                                                   TaskRunService taskRunService,
                                                   ExecutorService executionWorkers) {
        return new TaskDispatchService(taskRepository, projectProfileRepository, branchManager,
                taskRunService, executionWorkers);
    }
}
