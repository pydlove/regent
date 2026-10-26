package com.pydlove.regent.boot.config;

import com.pydlove.regent.gitcommit.application.command.GitCommitAuditService;
import com.pydlove.regent.gitcommit.application.query.GitCommitQueryRepository;
import com.pydlove.regent.gitcommit.application.query.GitCommitQueryService;
import com.pydlove.regent.gitcommit.domain.repository.GitCommitRecordRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Git 提交记录上下文显式装配。 */
@Configuration
public class GitCommitConfig {

    @Bean
    public GitCommitAuditService gitCommitAuditService(
            GitCommitRecordRepository repository,
            TransactionRunner transactionRunner) {
        return new GitCommitAuditService(repository, transactionRunner);
    }

    @Bean
    public GitCommitQueryService gitCommitQueryService(
            GitCommitQueryRepository repository) {
        return new GitCommitQueryService(repository);
    }
}
