package com.pydlove.regent.review.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.review.domain.model.ReviewDecision;
import com.pydlove.regent.review.domain.model.ReviewDecisionType;
import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffRequest;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewSnapshotStatus;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.port.ReviewDiffReader;
import com.pydlove.regent.review.domain.port.GitCommitGateway;
import com.pydlove.regent.review.domain.repository.ReviewDecisionRepository;
import com.pydlove.regent.review.domain.repository.ReviewSnapshotRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 正式部署前重新核对审阅结论。
 *
 * <p>快照状态只是第一道判断；真正决定能否部署的是“当前工作区 diff 是否仍等于通过时的哈希”。
 * 两者不一致时先把旧快照通过领域方法置为过期，再向调用方报错，避免旧结论继续被复用。
 */
public class ReviewGateService {

    private final ReviewSnapshotRepository snapshotRepository;
    private final ReviewDecisionRepository decisionRepository;
    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final ReviewDiffReader diffReader;
    private final GitCommitGateway commitGateway;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public ReviewGateService(ReviewSnapshotRepository snapshotRepository,
                             ReviewDecisionRepository decisionRepository,
                             TaskRepository taskRepository,
                             TaskRunRepository taskRunRepository,
                             ProjectProfileRepository projectProfileRepository,
                             ReviewDiffReader diffReader,
                             GitCommitGateway commitGateway,
                             TransactionRunner transactionRunner) {
        this(snapshotRepository, decisionRepository, taskRepository, taskRunRepository,
                projectProfileRepository, diffReader, commitGateway, transactionRunner,
                Clock.systemUTC());
    }

    ReviewGateService(ReviewSnapshotRepository snapshotRepository,
                      ReviewDecisionRepository decisionRepository,
                      TaskRepository taskRepository,
                      TaskRunRepository taskRunRepository,
                      ProjectProfileRepository projectProfileRepository,
                      ReviewDiffReader diffReader,
                      GitCommitGateway commitGateway,
                      TransactionRunner transactionRunner,
                      Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.decisionRepository = decisionRepository;
        this.taskRepository = taskRepository;
        this.taskRunRepository = taskRunRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.diffReader = diffReader;
        this.commitGateway = commitGateway;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public ReviewGateApproval requireApproved(TaskId taskId, UserId ownerId) {
        Task task = taskRepository.findByIdOwned(taskId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId.value()));
        if (task.getStatus() != TaskStatus.DONE) {
            throw new DomainException("只有已完成任务可以关联正式部署");
        }
        ReviewTaskId reviewTaskId = ReviewTaskId.of(taskId.value());
        ReviewSnapshot snapshot = snapshotRepository.findCurrentByTask(reviewTaskId)
                .orElseThrow(() -> new DomainException("尚未生成变更审阅快照，不能正式部署"));
        if (snapshot.getStatus() == ReviewSnapshotStatus.EXPIRED) {
            throw new DomainException("变更审阅已过期，请重新审阅后再部署");
        }
        if (!snapshot.isDeployable()) {
            throw new DomainException("任务尚未通过人工变更审阅，不能正式部署");
        }

        ReviewDecision decision = decisionRepository.findLatestBySnapshot(snapshot.getId())
                .orElseThrow(() -> new DomainException("审阅结论缺失，不能正式部署"));
        if (decision.getDecision() != ReviewDecisionType.APPROVED) {
            throw new DomainException("最新审阅结论不是通过，不能正式部署");
        }

        ProjectProfile project = projectProfileRepository.findById(task.getProjectId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "任务所属项目不存在：" + task.getProjectId().value()));
        if (snapshot.getStatus() == ReviewSnapshotStatus.COMMITTED) {
            if (!commitGateway.matchesCommit(
                    project.path(), snapshot.getCommitSha(), snapshot.reviewedFilePaths())) {
                expire(reviewTaskId);
                throw new DomainException("Git 提交后又发生变化，旧审阅结论已失效，请重新审阅");
            }
            return new ReviewGateApproval(taskId.value(), task.getProjectId().value(),
                    snapshot.getId().value(), decision.getId().value());
        }
        TaskRun run = taskRunRepository.findById(TaskRunId.of(snapshot.getTaskRunId().value()))
                .orElseThrow(() -> new DomainException("审阅快照关联的执行记录不存在"));
        ReviewDiff current = diffReader.read(new ReviewDiffRequest(
                project.path(), snapshot.getBaseRef(), run.getChangedFiles()));
        if (!snapshot.matchesDiffHash(current.diffHash())) {
            expire(reviewTaskId);
            throw new DomainException("代码在审阅后发生变化，旧结论已失效，请重新审阅");
        }
        return new ReviewGateApproval(taskId.value(), task.getProjectId().value(),
                snapshot.getId().value(), decision.getId().value());
    }

    private void expire(ReviewTaskId taskId) {
        transactionRunner.inTransaction(() -> {
            ReviewSnapshot fresh = snapshotRepository.findCurrentByTask(taskId)
                    .orElseThrow(() -> new DomainException("变更审阅快照已不存在"));
            fresh.expire(now());
            snapshotRepository.save(fresh);
            return null;
        });
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
