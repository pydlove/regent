package com.pydlove.regent.boot.config;

import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.execution.application.command.TaskCommandService;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.review.application.command.ReviewDecisionService;
import com.pydlove.regent.review.application.command.ReviewGateService;
import com.pydlove.regent.review.application.command.ReviewGitCommitService;
import com.pydlove.regent.review.application.command.ReviewSnapshotService;
import com.pydlove.regent.review.application.query.ReviewQueryService;
import com.pydlove.regent.review.domain.port.ReviewAnalyzer;
import com.pydlove.regent.review.domain.port.ReviewDiffReader;
import com.pydlove.regent.review.domain.port.GitCommitGateway;
import com.pydlove.regent.review.domain.repository.ReviewSnapshotRepository;
import com.pydlove.regent.review.domain.repository.ReviewDecisionRepository;
import com.pydlove.regent.review.domain.service.RuleBasedReviewAnalyzer;
import com.pydlove.regent.review.infrastructure.git.GitReviewDiffReader;
import com.pydlove.regent.review.infrastructure.git.GitCliCommitGateway;
import com.pydlove.regent.review.infrastructure.codex.CodexReviewAnalyzer;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.testing.domain.repository.TestCaseRunRepository;
import com.pydlove.regent.settings.application.command.GitCommitSettingsService;
import com.pydlove.regent.gitcommit.application.command.GitCommitAuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Path;
import java.time.Duration;

/** 审阅上下文显式装配。 */
@Configuration
public class ReviewConfig {

    @Bean
    public ReviewDiffReader reviewDiffReader() {
        return new GitReviewDiffReader();
    }

    @Bean
    public GitCommitGateway gitCommitGateway() {
        return new GitCliCommitGateway();
    }

    @Bean
    public ReviewAnalyzer reviewAnalyzer(
            @Value("${regent.execution.codex-command:codex}") String codexCommand,
            @Value("${regent.home}") String home,
            @Value("${regent.review.timeout:PT5M}") Duration timeout) {
        return new CodexReviewAnalyzer(
                codexCommand,
                Path.of(home, "review"),
                timeout,
                new RuleBasedReviewAnalyzer());
    }

    @Bean(name = "reviewWorkers", destroyMethod = "shutdown")
    public ExecutorService reviewWorkers(
            @Value("${regent.review.workers:2}") int workers) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "regent-review-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(Math.max(1, workers), factory);
    }

    @Bean
    public ReviewSnapshotService reviewSnapshotService(
            ReviewSnapshotRepository snapshotRepository,
            TaskRepository taskRepository,
            TaskRunRepository taskRunRepository,
            ProjectProfileRepository projectProfileRepository,
            RunEventRepository runEventRepository,
            ReviewDiffReader reviewDiffReader,
            ReviewAnalyzer reviewAnalyzer,
            TestCaseRunRepository testCaseRunRepository,
            ExecutorService reviewWorkers,
            TransactionRunner transactionRunner) {
        return new ReviewSnapshotService(
                snapshotRepository, taskRepository, taskRunRepository, projectProfileRepository,
                runEventRepository, reviewDiffReader, reviewAnalyzer, testCaseRunRepository,
                reviewWorkers, transactionRunner);
    }

    @Bean
    public ReviewQueryService reviewQueryService(
            ReviewSnapshotRepository snapshotRepository,
            ReviewDecisionRepository decisionRepository) {
        return new ReviewQueryService(snapshotRepository, decisionRepository);
    }

    @Bean
    public ReviewDecisionService reviewDecisionService(
            ReviewSnapshotRepository snapshotRepository,
            ReviewDecisionRepository decisionRepository,
            TaskCommandService taskCommandService,
            TransactionRunner transactionRunner) {
        return new ReviewDecisionService(
                snapshotRepository, decisionRepository, taskCommandService, transactionRunner);
    }

    @Bean
    public ReviewGateService reviewGateService(
            ReviewSnapshotRepository snapshotRepository,
            ReviewDecisionRepository decisionRepository,
            TaskRepository taskRepository,
            TaskRunRepository taskRunRepository,
            ProjectProfileRepository projectProfileRepository,
            ReviewDiffReader reviewDiffReader,
            GitCommitGateway gitCommitGateway,
            TransactionRunner transactionRunner) {
        return new ReviewGateService(
                snapshotRepository, decisionRepository, taskRepository, taskRunRepository,
                projectProfileRepository, reviewDiffReader, gitCommitGateway, transactionRunner);
    }

    @Bean
    public ReviewGitCommitService reviewGitCommitService(
            ReviewSnapshotRepository snapshotRepository,
            ReviewDecisionRepository decisionRepository,
            TaskRepository taskRepository,
            TaskRunRepository taskRunRepository,
            ProjectProfileRepository projectProfileRepository,
            ReviewDiffReader reviewDiffReader,
            GitCommitGateway gitCommitGateway,
            GitCommitAuditService gitCommitAuditService,
            GitCommitSettingsService settingsService,
            TransactionRunner transactionRunner) {
        return new ReviewGitCommitService(
                snapshotRepository, decisionRepository, taskRepository, taskRunRepository,
                projectProfileRepository, reviewDiffReader, gitCommitGateway,
                gitCommitAuditService, settingsService, transactionRunner);
    }
}
