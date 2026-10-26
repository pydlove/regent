package com.pydlove.regent.gitcommit.application.command;

import com.pydlove.regent.gitcommit.domain.model.GitCommitRecord;
import com.pydlove.regent.gitcommit.domain.model.GitCommitRecordId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitSnapshotId;
import com.pydlove.regent.gitcommit.domain.repository.GitCommitRecordRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 提交尝试审计用例。
 *
 * <p>外部 Git 进程不在数据库事务中执行：先落 RUNNING 记录，过程和结果分别由
 * {@link #succeed}、{@link #fail} 收敛。
 */
public class GitCommitAuditService {

    private final GitCommitRecordRepository repository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public GitCommitAuditService(GitCommitRecordRepository repository,
                                 TransactionRunner transactionRunner) {
        this(repository, transactionRunner, Clock.systemUTC());
    }

    GitCommitAuditService(GitCommitRecordRepository repository,
                          TransactionRunner transactionRunner,
                          Clock clock) {
        this.repository = repository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public GitCommitRecord begin(BeginGitCommitCommand command) {
        return transactionRunner.inTransaction(() -> {
            GitCommitSnapshotId snapshotId = command.snapshotId();
            if (repository.findSucceededBySnapshot(snapshotId).isPresent()) {
                throw new DomainException("当前审阅快照已经成功提交，不能重复提交");
            }
            if (repository.findRunningBySnapshot(snapshotId).isPresent()) {
                throw new DomainException("当前审阅快照已有提交正在执行，请稍后刷新");
            }
            GitCommitRecord record = GitCommitRecord.start(
                    command.projectId(),
                    command.taskId(),
                    command.taskRunId(),
                    snapshotId,
                    command.decisionId(),
                    command.operatorId(),
                    command.projectName(),
                    command.taskTitle(),
                    command.repositoryPath(),
                    command.ticket(),
                    command.message(),
                    command.branchName(),
                    command.baseRef(),
                    command.filePaths(),
                    now());
            return repository.save(record);
        });
    }

    public GitCommitRecord succeed(GitCommitRecordId id,
                                   String commitSha,
                                   String branchName,
                                   String output) {
        return transactionRunner.inTransaction(() -> {
            GitCommitRecord record = require(id);
            record.succeed(commitSha, branchName, output, now());
            return repository.save(record);
        });
    }

    public GitCommitRecord fail(GitCommitRecordId id, String reason, String output) {
        return transactionRunner.inTransaction(() -> {
            GitCommitRecord record = require(id);
            record.fail(reason, output, now());
            return repository.save(record);
        });
    }

    private GitCommitRecord require(GitCommitRecordId id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Git 提交记录不存在：" + id.value()));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
