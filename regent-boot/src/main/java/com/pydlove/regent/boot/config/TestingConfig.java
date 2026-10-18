package com.pydlove.regent.boot.config;

import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.testing.application.command.TaskTestCaseService;
import com.pydlove.regent.testing.application.command.TestCaseCommandService;
import com.pydlove.regent.testing.application.command.TestCaseExecutionService;
import com.pydlove.regent.testing.application.query.TestCaseQueryService;
import com.pydlove.regent.testing.application.query.TestCaseRunQueryService;
import com.pydlove.regent.testing.domain.port.TestCaseExecutor;
import com.pydlove.regent.testing.domain.repository.TaskTestCaseRepository;
import com.pydlove.regent.testing.domain.repository.TestCaseRepository;
import com.pydlove.regent.testing.domain.repository.TestCaseRunRepository;
import com.pydlove.regent.testing.infrastructure.process.ProcessTestCaseExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** 测试用例上下文装配。测试线程池与 codex 执行池分开，避免慢测试占住任务执行位。 */
@Configuration
public class TestingConfig {

    @Bean
    public TestCaseCommandService testCaseCommandService(TestCaseRepository testCaseRepository,
                                                         ProjectProfileRepository projectProfileRepository,
                                                         TransactionRunner transactionRunner) {
        return new TestCaseCommandService(
                testCaseRepository, projectProfileRepository, transactionRunner);
    }

    @Bean
    public TestCaseQueryService testCaseQueryService(TestCaseRepository testCaseRepository,
                                                     TestCaseRunRepository testCaseRunRepository,
                                                     ProjectProfileRepository projectProfileRepository) {
        return new TestCaseQueryService(
                testCaseRepository, testCaseRunRepository, projectProfileRepository);
    }

    @Bean
    public TestCaseRunQueryService testCaseRunQueryService(TestCaseRunRepository runRepository) {
        return new TestCaseRunQueryService(runRepository);
    }

    @Bean
    public TaskTestCaseService taskTestCaseService(TaskRepository taskRepository,
                                                   TestCaseRepository testCaseRepository,
                                                   TaskTestCaseRepository taskTestCaseRepository,
                                                   TransactionRunner transactionRunner) {
        return new TaskTestCaseService(
                taskRepository, testCaseRepository, taskTestCaseRepository, transactionRunner);
    }

    @Bean
    public TestCaseExecutor testCaseExecutor(@Value("${regent.home}") String home,
                                             @Value("${regent.testing.timeout:PT10M}") Duration timeout,
                                             @Value("${regent.testing.max-output-bytes:262144}") int maxOutputBytes) {
        return new ProcessTestCaseExecutor(
                Path.of(home, "test-cases", "runs"), timeout, maxOutputBytes);
    }

    @Bean(name = "testingWorkers", destroyMethod = "shutdown")
    public ExecutorService testingWorkers(
            @Value("${regent.testing.workers:2}") int workers) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "regent-test-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(Math.max(1, workers), factory);
    }

    @Bean
    public TestCaseExecutionService testCaseExecutionService(
            TaskRepository taskRepository,
            ProjectProfileRepository projectProfileRepository,
            EnvironmentEntryRepository environmentEntryRepository,
            TaskTestCaseRepository taskTestCaseRepository,
            TestCaseRepository testCaseRepository,
            TestCaseRunRepository testCaseRunRepository,
            TestCaseExecutor testCaseExecutor,
            ExecutorService testingWorkers,
            TransactionRunner transactionRunner) {
        return new TestCaseExecutionService(
                taskRepository,
                projectProfileRepository,
                environmentEntryRepository,
                taskTestCaseRepository,
                testCaseRepository,
                testCaseRunRepository,
                testCaseExecutor,
                testingWorkers,
                transactionRunner);
    }
}
