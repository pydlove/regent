package com.pydlove.regent.review.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.gitcommit.application.command.BeginGitCommitCommand;
import com.pydlove.regent.gitcommit.application.command.GitCommitAuditService;
import com.pydlove.regent.gitcommit.domain.model.GitCommitDecisionId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitOperatorId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitProjectId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitRecord;
import com.pydlove.regent.gitcommit.domain.model.GitCommitSnapshotId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitTaskId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitTaskRunId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.review.domain.model.ReviewDecision;
import com.pydlove.regent.review.domain.model.ReviewDecisionType;
import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffRequest;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewSnapshotStatus;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewTaskRunId;
import com.pydlove.regent.review.domain.port.GitCommitGateway;
import com.pydlove.regent.review.domain.port.ReviewDiffReader;
import com.pydlove.regent.review.domain.repository.ReviewDecisionRepository;
import com.pydlove.regent.review.domain.repository.ReviewSnapshotRepository;
import com.pydlove.regent.settings.application.command.GitCommitSettingsService;
import com.pydlove.regent.settings.domain.model.GitCommitSettings;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * 审阅通过后的本地 Git 提交闭环。
 *
 * <p>提交不是部署门禁的另一条旁路：动作始终绑定当前审阅快照。平台先复核 diff 哈希、
 * 最新人工结论与暂存区，再只把快照覆盖的文件加入索引，避免把未审阅改动混入提交。
 */
public class ReviewGitCommitService {

    private final ReviewSnapshotRepository snapshotRepository;
    private final ReviewDecisionRepository decisionRepository;
    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final ReviewDiffReader diffReader;
    private final GitCommitGateway commitGateway;
    private final GitCommitAuditService auditService;
    private final GitCommitSettingsService settingsService;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public ReviewGitCommitService(ReviewSnapshotRepository snapshotRepository,
                                  ReviewDecisionRepository decisionRepository,
                                  TaskRepository taskRepository,
                                  TaskRunRepository taskRunRepository,
                                  ProjectProfileRepository projectProfileRepository,
                                  ReviewDiffReader diffReader,
                                  GitCommitGateway commitGateway,
                                  GitCommitAuditService auditService,
                                  GitCommitSettingsService settingsService,
                                  TransactionRunner transactionRunner) {
        this(snapshotRepository, decisionRepository, taskRepository, taskRunRepository,
                projectProfileRepository, diffReader, commitGateway, auditService,
                settingsService, transactionRunner, Clock.systemUTC());
    }

    ReviewGitCommitService(ReviewSnapshotRepository snapshotRepository,
                           ReviewDecisionRepository decisionRepository,
                           TaskRepository taskRepository,
                           TaskRunRepository taskRunRepository,
                           ProjectProfileRepository projectProfileRepository,
                           ReviewDiffReader diffReader,
                           GitCommitGateway commitGateway,
                           GitCommitAuditService auditService,
                           GitCommitSettingsService settingsService,
                           TransactionRunner transactionRunner,
                           Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.decisionRepository = decisionRepository;
        this.taskRepository = taskRepository;
        this.taskRunRepository = taskRunRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.diffReader = diffReader;
        this.commitGateway = commitGateway;
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public ReviewGitCommitView preview(ReviewTaskId taskId, UserId ownerId) {
        Prepared prepared = prepare(taskId, ownerId);
        return view(prepared);
    }

    public ReviewGitCommitView commit(ReviewGitCommitCommand command) {
        UserId ownerId = UserId.of(command.operatorId().value());
        Prepared prepared = prepare(command.taskId(), ownerId);
        GitCommitSettings settings = prepared.settings();
        settings.validateMessage(command.message());
        if (!prepared.view().canCommit()) {
            throw new DomainException(prepared.view().reason());
        }

        ReviewDecision decision = requireNotNull(prepared.decision(), "审阅结论");
        GitCommitRecord record = auditService.begin(new BeginGitCommitCommand(
                GitCommitProjectId.of(prepared.project().id().value()),
                GitCommitTaskId.of(prepared.task().getId().value()),
                GitCommitTaskRunId.of(prepared.snapshot().getTaskRunId().value()),
                GitCommitSnapshotId.of(prepared.snapshot().getId().value()),
                GitCommitDecisionId.of(decision.getId().value()),
                GitCommitOperatorId.of(command.operatorId().value()),
                prepared.project().name(),
                prepared.task().getTitle(),
                prepared.project().path(),
                prepared.view().ticket(),
                command.message().strip(),
                prepared.view().branchName(),
                prepared.snapshot().getBaseRef(),
                prepared.snapshot().reviewedFilePaths()));

        GitCommitGateway.CommitResult result;
        try {
            result = commitGateway.commit(
                    prepared.project().path(),
                    prepared.snapshot().reviewedFilePaths(),
                    command.message().strip());
        } catch (RuntimeException failure) {
            try {
                auditService.fail(record.getId(), safeFailureMessage(failure), null);
            } catch (RuntimeException auditFailure) {
                failure.addSuppressed(auditFailure);
            }
            throw failure;
        }
        auditService.succeed(record.getId(), result.commitSha(), result.branchName(), null);

        transactionRunner.inTransaction(() -> {
            ReviewSnapshot current = snapshotRepository.findCurrentByTask(command.taskId())
                    .orElseThrow(() -> new DomainException("审阅快照已不存在，Git 提交结果未登记"));
            current.markCommitted(command.operatorId(), result.commitSha(),
                    command.message().strip(), now());
            snapshotRepository.save(current);
            return null;
        });
        return preview(command.taskId(), ownerId);
    }

    private Prepared prepare(ReviewTaskId taskId, UserId ownerId) {
        Task task = taskRepository.findByIdOwned(com.pydlove.regent.execution.domain.model.TaskId.of(
                        taskId.value()), ownerId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId.value()));
        if (task.getStatus() != TaskStatus.DONE) {
            throw new DomainException("只有已完成任务可以在审阅通过后提交 Git");
        }
        ReviewSnapshot snapshot = snapshotRepository.findCurrentByTask(taskId)
                .orElseThrow(() -> new DomainException("尚未生成变更审阅快照"));
        ProjectProfile project = projectProfileRepository.findById(
                        ProjectId.of(snapshot.getProjectId().value()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "审阅关联项目不存在：" + snapshot.getProjectId().value()));
        GitCommitSettings settings = settingsService.current();

        if (!settings.enabled()) {
            return unavailable(task, project, snapshot, null, settings,
                    "当前未启用审阅后 Git 提交");
        }
        if (!snapshot.isDeployable()) {
            return unavailable(task, project, snapshot, null, settings, "变更审阅尚未通过");
        }

        ReviewDecision decision = decisionRepository.findLatestBySnapshot(snapshot.getId())
                .orElseThrow(() -> new DomainException("审阅结论缺失，不能提交 Git"));
        if (decision.getDecision() != ReviewDecisionType.APPROVED) {
            throw new DomainException("最新审阅结论不是通过，不能提交 Git");
        }

        List<String> files = snapshot.reviewedFilePaths();
        if (files.isEmpty()) {
            return unavailable(task, project, snapshot, decision, settings,
                    "审阅快照没有可提交的文件");
        }

        if (snapshot.getStatus() == ReviewSnapshotStatus.COMMITTED) {
            if (!commitGateway.matchesCommit(project.path(), snapshot.getCommitSha(), files)) {
                expire(taskId);
                throw new DomainException("Git 提交后又发生变化，原审阅结论已失效");
            }
            return unavailable(task, project, snapshot, decision, settings,
                    "当前审阅快照已经提交 Git");
        }

        TaskRun run = taskRunRepository.findById(TaskRunId.of(snapshot.getTaskRunId().value()))
                .orElseThrow(() -> new DomainException("审阅快照关联的执行记录不存在"));
        ReviewDiff current = diffReader.read(new ReviewDiffRequest(
                project.path(), snapshot.getBaseRef(), run.getChangedFiles()));
        if (!snapshot.matchesDiffHash(current.diffHash())) {
            expire(taskId);
            throw new DomainException("代码在审阅后发生变化，旧结论已失效，请重新审阅");
        }

        GitCommitGateway.Inspection inspection = commitGateway.inspect(project.path(), files);
        String ticket = ticketOf(task);
        String suggested = settings.render(
                Long.toString(taskId.value()),
                ticket,
                task.getTitle(),
                project.code(),
                project.name(),
                files);
        String reason = "";
        boolean canCommit = true;
        if (!inspection.gitRepository()) {
            canCommit = false;
            reason = "项目目录不是可提交的 Git 工作区";
        } else if (inspection.branchName().isBlank()) {
            canCommit = false;
            reason = "仓库处于 detached HEAD，请先切换到分支";
        } else if (!inspection.indexClean()) {
            canCommit = false;
            reason = stagedReason(inspection.stagedPaths());
        }
        return new Prepared(task, project, snapshot, decision, settings,
                new ReviewGitCommitView(
                        true,
                        canCommit,
                        reason,
                        project.name(),
                        inspection.branchName(),
                        inspection.headCommit(),
                        ticket,
                        files.size(),
                        files,
                        suggested,
                        settings.validationPattern(),
                        settings.validationHint(),
                        "",
                        null));
    }

    private ReviewGitCommitView view(Prepared prepared) {
        return prepared.view();
    }

    private Prepared unavailable(Task task,
                                 ProjectProfile project,
                                 ReviewSnapshot snapshot,
                                 ReviewDecision decision,
                                 GitCommitSettings settings,
                                 String reason) {
        return new Prepared(task, project, snapshot, decision, settings,
                new ReviewGitCommitView(
                        settings.enabled(),
                        false,
                        reason,
                        project.name(),
                        "",
                        snapshot.getHeadRef() == null ? "" : snapshot.getHeadRef(),
                        "",
                        snapshot.reviewedFilePaths().size(),
                        snapshot.reviewedFilePaths(),
                        "",
                        settings.validationPattern(),
                        settings.validationHint(),
                        snapshot.getCommitSha() == null ? "" : snapshot.getCommitSha(),
                        snapshot.getCommittedAt()));
    }

    private void expire(ReviewTaskId taskId) {
        transactionRunner.inTransaction(() -> {
            ReviewSnapshot current = snapshotRepository.findCurrentByTask(taskId)
                    .orElseThrow(() -> new DomainException("审阅快照已不存在"));
            current.expire(now());
            snapshotRepository.save(current);
            return null;
        });
    }

    private static String stagedReason(List<String> stagedPaths) {
        if (stagedPaths.isEmpty()) {
            return "存在已暂存改动，请先提交或取消暂存后再试";
        }
        String preview = String.join("、", stagedPaths.stream().limit(3).toList());
        return "存在已暂存改动，请先提交或取消暂存：" + preview
                + (stagedPaths.size() > 3 ? " 等 " + stagedPaths.size() + " 个文件" : "");
    }

    private static String ticketOf(Task task) {
        return task.getBugId()
                .map(bugId -> bugId.value())
                .filter(value -> value > 0 && value <= 999_999)
                .map(value -> String.format(Locale.ROOT, "%06d", value))
                .orElse("");
    }

    private static String safeFailureMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return "Git 提交执行失败";
        }
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private record Prepared(
            Task task,
            ProjectProfile project,
            ReviewSnapshot snapshot,
            ReviewDecision decision,
            GitCommitSettings settings,
            ReviewGitCommitView view) {
    }
}
