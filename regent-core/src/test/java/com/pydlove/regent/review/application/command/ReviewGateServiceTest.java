package com.pydlove.regent.review.application.command;

import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.review.domain.model.ReviewDecision;
import com.pydlove.regent.review.domain.model.ReviewDecisionId;
import com.pydlove.regent.review.domain.model.ReviewDecisionType;
import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffRequest;
import com.pydlove.regent.review.domain.model.ReviewOperatorId;
import com.pydlove.regent.review.domain.model.ReviewProjectId;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewSnapshotId;
import com.pydlove.regent.review.domain.model.ReviewSnapshotStatus;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewTaskRunId;
import com.pydlove.regent.review.domain.repository.ReviewDecisionRepository;
import com.pydlove.regent.review.domain.repository.ReviewSnapshotRepository;
import com.pydlove.regent.review.domain.port.GitCommitGateway;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReviewGateServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);
    private static final TaskId TASK = TaskId.of(1L);
    private static final String HASH_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String COMMIT =
            "cccccccccccccccccccccccccccccccccccccccc";

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Runs runs = new ExecutionFakes.Runs();
    private final ExecutionFakes.Projects projects = new ExecutionFakes.Projects();
    private final Snapshots snapshots = new Snapshots();
    private final Decisions decisions = new Decisions();
    private boolean committedMatches = true;
    private TaskRun run;

    @BeforeEach
    void setUp() {
        Task task = Task.manual(PROJECT, "部署任务", "完成变更", 0);
        task.assignIdentity(TASK);
        task.dispatch(null);
        task.complete();
        tasks.add(task);
        projects.put(ExecutionFakes.Projects.profile(1L, DecisionLevel.L1, true));
        run = TaskRun.start(TASK, 1, null, "base");
        runs.save(run);
        run.finish(new ExecResult(RunStatus.SUCCESS, 0, "thread", TokenUsage.ZERO,
                "完成", "{}", true, List.of("Task.java"), List.of(), null));
    }

    @Test
    void 无有效快照时失败关闭() {
        assertThrows(DomainException.class,
                () -> service(HASH_A).requireApproved(TASK, UserId.of(7)));
    }

    @Test
    void 已通过且哈希一致时返回部署审计引用() {
        ReviewSnapshot snapshot = approvedSnapshot();
        ReviewDecision decision = saveDecision(snapshot);

        ReviewGateApproval approval = service(HASH_A).requireApproved(TASK, UserId.of(7));

        assertEquals(snapshot.getId().value(), approval.snapshotId());
        assertEquals(decision.getId().value(), approval.decisionId());
    }

    @Test
    void 代码变化后旧快照过期并阻断部署() {
        ReviewSnapshot snapshot = approvedSnapshot();
        saveDecision(snapshot);

        assertThrows(DomainException.class,
                () -> service(HASH_B).requireApproved(TASK, UserId.of(7)));

        assertEquals(ReviewSnapshotStatus.EXPIRED, snapshot.getStatus());
    }

    @Test
    void 已提交且审阅文件仍与提交一致时允许部署() {
        ReviewSnapshot snapshot = approvedSnapshot();
        snapshot.markCommitted(ReviewOperatorId.of(7), COMMIT, "feat(test): 完成",
                Instant.parse("2026-10-21T06:02:00Z"));
        saveDecision(snapshot);

        ReviewGateApproval approval = service(HASH_B).requireApproved(TASK, UserId.of(7));

        assertEquals(snapshot.getId().value(), approval.snapshotId());
        assertEquals(ReviewSnapshotStatus.COMMITTED, snapshot.getStatus());
    }

    @Test
    void 提交后文件再次变化时快照过期() {
        ReviewSnapshot snapshot = approvedSnapshot();
        snapshot.markCommitted(ReviewOperatorId.of(7), COMMIT, "feat(test): 完成",
                Instant.parse("2026-10-21T06:02:00Z"));
        saveDecision(snapshot);
        committedMatches = false;

        assertThrows(DomainException.class,
                () -> service(HASH_B).requireApproved(TASK, UserId.of(7)));
        assertEquals(ReviewSnapshotStatus.EXPIRED, snapshot.getStatus());
    }

    private ReviewGateService service(String currentHash) {
        return new ReviewGateService(snapshots, decisions, tasks, runs, projects,
                request -> diff(currentHash, run),
                new GitCommitGateway() {
                    @Override
                    public Inspection inspect(String projectDirectory, List<String> reviewedPaths) {
                        return new Inspection(true, "main", "head", true, List.of());
                    }

                    @Override
                    public CommitResult commit(String projectDirectory,
                                               List<String> reviewedPaths,
                                               String message) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean matchesCommit(String projectDirectory,
                                                 String commitSha,
                                                 List<String> reviewedPaths) {
                        return committedMatches;
                    }
                },
                new ExecutionFakes.Transactions());
    }

    private ReviewSnapshot approvedSnapshot() {
        ReviewSnapshot snapshot = ReviewSnapshot.create(ReviewTaskId.of(1),
                ReviewTaskRunId.of(run.getId().value()), ReviewProjectId.of(1),
                diff(HASH_A, run), Instant.parse("2026-10-21T06:00:00Z"));
        snapshot.approve(Instant.parse("2026-10-21T06:01:00Z"));
        return snapshots.save(snapshot);
    }

    private ReviewDecision saveDecision(ReviewSnapshot snapshot) {
        return decisions.save(ReviewDecision.create(snapshot.getId(),
                ReviewDecisionType.APPROVED, ReviewOperatorId.of(7), null,
                Instant.parse("2026-10-21T06:01:00Z")));
    }

    private static ReviewDiff diff(String hash, TaskRun run) {
        return new ReviewDiff("base", "head", true, hash,
                List.of(new com.pydlove.regent.review.domain.model.ReviewDiffFile(
                        "Task.java", 1, 0, false, false)),
                1, 0, "diff", false);
    }

    private static final class Snapshots implements ReviewSnapshotRepository {
        private ReviewSnapshot snapshot;
        private long sequence;

        @Override public Optional<ReviewSnapshot> findById(ReviewSnapshotId id) {
            return snapshot != null && snapshot.getId().equals(id) ? Optional.of(snapshot) : Optional.empty();
        }
        @Override public Optional<ReviewSnapshot> findByTask(ReviewTaskId taskId) { return findCurrentByTask(taskId); }
        @Override public Optional<ReviewSnapshot> findCurrentByTask(ReviewTaskId taskId) {
            return snapshot != null && snapshot.getTaskId().equals(taskId)
                    ? Optional.of(snapshot) : Optional.empty();
        }
        @Override public Optional<ReviewSnapshot> findByTaskRun(ReviewTaskRunId taskRunId) {
            return snapshot != null && snapshot.getTaskRunId().equals(taskRunId)
                    ? Optional.of(snapshot) : Optional.empty();
        }
        @Override public ReviewSnapshot save(ReviewSnapshot value) {
            if (value.getId() == null) value.assignIdentity(ReviewSnapshotId.of(++sequence));
            snapshot = value;
            return value;
        }
        @Override public List<ReviewSnapshot> findUnexpiredByTask(ReviewTaskId taskId) {
            return snapshot == null || snapshot.getStatus() == ReviewSnapshotStatus.EXPIRED
                    ? List.of() : List.of(snapshot);
        }
    }

    private static final class Decisions implements ReviewDecisionRepository {
        private ReviewDecision decision;
        private long sequence;

        @Override public ReviewDecision save(ReviewDecision value) {
            if (value.getId() == null) value.assignIdentity(ReviewDecisionId.of(++sequence));
            decision = value;
            return value;
        }
        @Override public List<ReviewDecision> findBySnapshot(ReviewSnapshotId snapshotId) {
            return decision == null ? List.of() : List.of(decision);
        }
        @Override public Optional<ReviewDecision> findLatestBySnapshot(ReviewSnapshotId snapshotId) {
            return Optional.ofNullable(decision);
        }
    }
}
