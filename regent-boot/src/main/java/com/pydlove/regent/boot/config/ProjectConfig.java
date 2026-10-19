package com.pydlove.regent.boot.config;

import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.project.application.command.ProjectSummaryGenerationService;
import com.pydlove.regent.project.domain.port.ProjectSummaryGenerator;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.project.infrastructure.summary.CodexProjectSummaryGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** 项目资料生成链路的显式装配。 */
@Configuration
public class ProjectConfig {

    private static final Logger log = LoggerFactory.getLogger(ProjectConfig.class);

    @Bean(destroyMethod = "shutdown")
    public ExecutorService projectSummaryWorkers(
            @Value("${regent.project.summary.workers:1}") int workers) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable,
                    "regent-project-summary-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(Math.max(1, workers), factory);
    }

    @Bean
    public ProjectSummaryGenerator projectSummaryGenerator(
            AgentExecutor agentExecutor,
            @Value("${regent.project.summary.timeout:PT10M}") Duration timeout) {
        return new CodexProjectSummaryGenerator(agentExecutor, timeout);
    }

    @Bean
    public ProjectSummaryGenerationService projectSummaryGenerationService(
            ProjectRepository projectRepository,
            ProjectSummaryGenerator projectSummaryGenerator,
            @Qualifier("projectSummaryWorkers") ExecutorService workers) {
        return new ProjectSummaryGenerationService(
                projectRepository, projectSummaryGenerator, workers);
    }

    /**
     * 上一进程中的 Codex 进程已不存在，生成中状态不能永久占用“重试”入口。
     * 启动时统一收敛为失败，用户可在项目详情里重新生成。
     */
    @Bean
    public ApplicationRunner projectSummaryRecoveryRunner(
            ProjectSummaryGenerationService summaryGenerationService) {
        return args -> {
            try {
                int recovered = summaryGenerationService.recoverInterrupted();
                if (recovered > 0) {
                    log.info("项目资料启动恢复完成：{} 条生成中记录已标记为失败", recovered);
                }
            } catch (RuntimeException e) {
                log.warn("项目资料启动恢复失败：{}", e.getMessage());
            }
        };
    }
}
