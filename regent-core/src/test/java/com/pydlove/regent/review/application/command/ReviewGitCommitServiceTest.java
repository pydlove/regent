package com.pydlove.regent.review.application.command;

import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.gitcommit.application.command.GitCommitAuditService;
import com.pydlove.regent.gitcommit.domain.model.GitCommitRecord;
import com.pydlove.regent.gitcommit.domain.model.GitCommitRecordId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitSnapshotId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitStatus;
import com.pydlove.regent.gitcommit.domain.repository.GitCommitRecordRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.review.domain.model.ReviewDecision;
import com.pydlove.regent.review.domain.model.ReviewDecisionId;
import com.pydlove.regent.review.domain.model.ReviewDecisionType;
import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffFile;
import com.pydlove.regent.review.domain.model.ReviewOperatorId;
import com.pydlove.regent.review.domain.model.ReviewProjectId;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewSnapshotId;
import com.pydlove.regent.review.domain.model.ReviewSnapshotStatus;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewTaskRunId;
import com.pydlove.regent.review.domain.port.GitCommitGateway;
import com.pydlove.regent.review.domain.repository.ReviewDecisionRepository;
import com.pydlove.regent.review.domain.repository.ReviewSnapshotRepository;
import com.pydlove.regent.settings.application.command.GitCommitSettingsService;
import com.pydlove.regent.settings.domain.model.GitCommitSettings;
import com.pydlove.regent.settings.domain.repository.GitCommitSettingsRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.shared.kernel.TokenUsage;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewGitCommitServiceTest {

    private static final TaskId TASK_ID = TaskId.of(181);
    private static final String HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String COMMIT =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Runs runs = new ExecutionFakes.Runs();
    private final ExecutionFakes.Projects projects = new ExecutionFakes.Projects();
    private final Snapshots snapshots = new Snapshots();
    private final Decisions decisions = new Decisions();
    private final Settings settings = new Settings();
    private final Gateway gateway = new Gateway();
    private final Commits commits = new Commits();
    private TaskRun run;

    @BeforeEach
    void setUp() {
        Task task = Task.manual(ProjectId.of(1), "完善变更审阅", "实现提交闭环", 0);
        task.assignIdentity(TASK_ID);
        task.dispatch(null);
        task.complete();
        tasks.add(task);
        projects.put(new com.pydlove.regent.project.domain.repository.ProjectProfile(
                ProjectId.of(1), UserId.of(7), "regent", "Regent",
                "/tmp/regent-project-1", DecisionLevel.L1, true,
                com.pydlove.regent.project.domain.model.ProjectSummaryStatus.NOT_GENERATED,
                null, null, null));
        run = TaskRun.start(TASK_ID, 1, "main", "base");
        runs.save(run);
        run.finish(new ExecResult(RunStatus.SUCCESS, 0, "thread", TokenUsage.ZERO,
                "完成", "{}", true, List.of("A.java"), List.of(), null));

        ReviewSnapshot snapshot = ReviewSnapshot.create(
                ReviewTaskId.of(TASK_ID.value()),
                ReviewTaskRunId.of(run.getId().value()),
                ReviewProjectId.of(1),
                new ReviewDiff("base", "head", true, HASH,
                        List.of(new ReviewDiffFile("A.java", 3, 1, false, false)),
                        3, 1, "diff", false),
                Instant.parse("2026-10-21T06:00:00Z"));
        snapshot.approve(Instant.parse("2026-10-21T06:01:00Z"));
        snapshots.save(snapshot);
        decisions.save(ReviewDecision.create(snapshot.getId(), ReviewDecisionType.APPROVED,
                ReviewOperatorId.of(7), null, Instant.parse("2026-10-21T06:01:00Z")));
    }

    @Test
    void 通过后生成提交说明并只展示审阅文件() {
        ReviewGitCommitView view = service().preview(
                ReviewTaskId.of(TASK_ID.value()), UserId.of(7));

        assertTrue(view.canCommit());
        assertEquals("feat: 完善变更审阅", view.suggestedMessage());
        assertEquals(List.of("A.java"), view.filePaths());
        assertEquals("main", view.branchName());
    }

    @Test
    void 关联Bug时自动生成六位编号() {
        Task bugTask = Task.fromBug(
                ProjectId.of(1), BugId.of(102008), "完善变更审阅", "修复缺陷", 0);
        bugTask.assignIdentity(TASK_ID);
        bugTask.dispatch(null);
        bugTask.complete();
        tasks.add(bugTask);

        ReviewGitCommitView view = service().preview(
                ReviewTaskId.of(TASK_ID.value()), UserId.of(7));

        assertTrue(view.canCommit());
        assertEquals("102008", view.ticket());
        assertEquals("feat(102008): 完善变更审阅", view.suggestedMessage());
    }

    @Test
    void 提交后快照进入已提交状态并记录审计字段() {
        ReviewGitCommitView committed = service().commit(new ReviewGitCommitCommand(
                ReviewTaskId.of(TASK_ID.value()),
                ReviewOperatorId.of(7),
                "feat: 完善变更审阅"));

        assertEquals(COMMIT, committed.commitSha());
        assertEquals(ReviewSnapshotStatus.COMMITTED, snapshots.snapshot.getStatus());
        assertEquals("feat: 完善变更审阅", snapshots.snapshot.getCommitMessage());
        assertEquals(7, snapshots.snapshot.getCommittedBy().value());
        assertEquals(GitCommitStatus.SUCCEEDED, commits.record.getStatus());
        assertEquals(COMMIT, commits.record.getCommitSha());
    }

    @Test
    void 暂存区已有改动时不提交() {
        gateway.indexClean = false;

        ReviewGitCommitView view = service().preview(
                ReviewTaskId.of(TASK_ID.value()), UserId.of(7));

        assertFalse(view.canCommit());
        assertTrue(view.reason().contains("已暂存"));
        assertThrows(DomainException.class, () -> service().commit(new ReviewGitCommitCommand(
                ReviewTaskId.of(TASK_ID.value()),
                ReviewOperatorId.of(7),
                "feat: 完善变更审阅")));
    }

    @Test
    void 不符合企业规范时在调用Git前失败() {
        assertThrows(DomainException.class, () -> service().commit(new ReviewGitCommitCommand(
                ReviewTaskId.of(TASK_ID.value()),
                ReviewOperatorId.of(7),
                "随手写一句")));
    }

    @Test
    void 未关联Bug时自动生成不带编号的提交说明() {
        ReviewGitCommitView view = service().preview(
                ReviewTaskId.of(TASK_ID.value()), UserId.of(7));

        assertTrue(view.canCommit());
        assertEquals("", view.ticket());
        assertEquals("feat: 完善变更审阅", view.suggestedMessage());
    }

    private ReviewGitCommitService service() {
        return new ReviewGitCommitService(
                snapshots, decisions, tasks, runs, projects,
                request -> new ReviewDiff("base", "head", true, HASH,
                        List.of(new ReviewDiffFile("A.java", 3, 1, false, false)),
                        3, 1, "diff", false),
                gateway,
                new GitCommitAuditService(commits, new ExecutionFakes.Transactions()),
                new GitCommitSettingsService(settings),
                new ExecutionFakes.Transactions());
    }

    private static final class Snapshots implements ReviewSnapshotRepository {
        private ReviewSnapshot snapshot;

        @Override
        public Optional<ReviewSnapshot> findById(ReviewSnapshotId id) {
            return snapshot != null && snapshot.getId().equals(id)
                    ? Optional.of(snapshot) : Optional.empty();
        }

        @Override
        public Optional<ReviewSnapshot> findByTask(ReviewTaskId taskId) {
            return findCurrentByTask(taskId);
        }

        @Override
        public Optional<ReviewSnapshot> findCurrentByTask(ReviewTaskId taskId) {
            return snapshot != null && snapshot.getTaskId().equals(taskId)
                    ? Optional.of(snapshot) : Optional.empty();
        }

        @Override
        public Optional<ReviewSnapshot> findByTaskRun(ReviewTaskRunId taskRunId) {
            return snapshot != null && snapshot.getTaskRunId().equals(taskRunId)
                    ? Optional.of(snapshot) : Optional.empty();
        }

        @Override
        public ReviewSnapshot save(ReviewSnapshot snapshot) {
            if (snapshot.getId() == null) snapshot.assignIdentity(ReviewSnapshotId.of(1));
            this.snapshot = snapshot;
            return snapshot;
        }

        @Override
        public List<ReviewSnapshot> findUnexpiredByTask(ReviewTaskId taskId) {
            return snapshot == null || snapshot.getStatus() == ReviewSnapshotStatus.EXPIRED
                    ? List.of() : List.of(snapshot);
        }
    }

    private static final class Decisions implements ReviewDecisionRepository {
        private ReviewDecision decision;

        @Override
        public ReviewDecision save(ReviewDecision decision) {
            if (decision.getId() == null) decision.assignIdentity(ReviewDecisionId.of(1));
            this.decision = decision;
            return decision;
        }

        @Override
        public List<ReviewDecision> findBySnapshot(ReviewSnapshotId snapshotId) {
            return decision == null ? List.of() : List.of(decision);
        }

        @Override
        public Optional<ReviewDecision> findLatestBySnapshot(ReviewSnapshotId snapshotId) {
            return Optional.ofNullable(decision);
        }
    }

    private static final class Settings implements GitCommitSettingsRepository {
        private GitCommitSettings value = GitCommitSettings.defaults();

        @Override
        public Optional<GitCommitSettings> find() {
            return Optional.of(value);
        }

        @Override
        public void save(GitCommitSettings settings) {
            value = settings;
        }
    }

    private static final class Gateway implements GitCommitGateway {
        private boolean indexClean = true;
        private boolean committed;

        @Override
        public Inspection inspect(String projectDirectory, List<String> reviewedPaths) {
            return new Inspection(true, "main", "head", indexClean,
                    indexClean ? List.of() : List.of("Other.java"));
        }

        @Override
        public CommitResult commit(String projectDirectory,
                                   List<String> reviewedPaths,
                                   String message) {
            committed = true;
            return new CommitResult(COMMIT, "main");
        }

        @Override
        public boolean matchesCommit(String projectDirectory,
                                     String commitSha,
                                     List<String> reviewedPaths) {
            return committed && COMMIT.equals(commitSha);
        }
    }

    private static final class Commits implements GitCommitRecordRepository {
        private GitCommitRecord record;
        private long sequence;

        @Override
        public GitCommitRecord save(GitCommitRecord record) {
            if (record.getId() == null) {
                record.assignIdentity(GitCommitRecordId.of(++sequence));
            }
            this.record = record;
            return record;
        }

        @Override
        public Optional<GitCommitRecord> findById(GitCommitRecordId id) {
            return record != null && record.getId().equals(id)
                    ? Optional.of(record) : Optional.empty();
        }

        @Override
        public Optional<GitCommitRecord> findSucceededBySnapshot(GitCommitSnapshotId snapshotId) {
            return record != null && record.getSnapshotId().equals(snapshotId)
                    && record.getStatus() == GitCommitStatus.SUCCEEDED
                    ? Optional.of(record) : Optional.empty();
        }

        @Override
        public Optional<GitCommitRecord> findRunningBySnapshot(GitCommitSnapshotId snapshotId) {
            return record != null && record.getSnapshotId().equals(snapshotId)
                    && record.getStatus() == GitCommitStatus.RUNNING
                    ? Optional.of(record) : Optional.empty();
        }
    }
}
