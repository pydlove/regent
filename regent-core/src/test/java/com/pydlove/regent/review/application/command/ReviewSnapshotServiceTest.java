package com.pydlove.regent.review.application.command;

import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.review.domain.model.ReviewAnalysisStatus;
import com.pydlove.regent.review.domain.model.ReviewChangeUnitId;
import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffFile;
import com.pydlove.regent.review.domain.model.ReviewDiffRequest;
import com.pydlove.regent.review.domain.model.ReviewEvidenceId;
import com.pydlove.regent.review.domain.model.ReviewFindingId;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewSnapshotId;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewTaskRunId;
import com.pydlove.regent.review.domain.repository.ReviewSnapshotRepository;
import com.pydlove.regent.review.domain.service.RuleBasedReviewAnalyzer;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.shared.kernel.TokenUsage;
import com.pydlove.regent.support.ExecutionFakes;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseBinding;
import com.pydlove.regent.testing.domain.model.TestCaseExecutionResult;
import com.pydlove.regent.testing.domain.model.TestCaseRun;
import com.pydlove.regent.testing.domain.model.TestCaseRunId;
import com.pydlove.regent.testing.domain.model.TestCaseRunStatus;
import com.pydlove.regent.testing.domain.model.TestCaseType;
import com.pydlove.regent.testing.domain.repository.TestCaseRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewSnapshotServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);
    private static final TaskId TASK = TaskId.of(1L);
    private static final String HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Runs runs = new ExecutionFakes.Runs();
    private final ExecutionFakes.Projects projects = new ExecutionFakes.Projects();
    private final InMemorySnapshots snapshots = new InMemorySnapshots();
    private final Events events = new Events();
    private final DirectExecutor executor = new DirectExecutor();
    private final TestCaseRuns testCases = new TestCaseRuns();
    private final ExecutionFakes.Transactions transactions = new ExecutionFakes.Transactions();

    @BeforeEach
    void setUp() {
        tasks.wire(runs);
        projects.put(ExecutionFakes.Projects.profile(1L, DecisionLevel.L1, true));
    }

    @Test
    void 成功收场后生成带分组和证据的快照() {
        Task task = completedTask();
        TaskRun run = successfulRun(task);
        ReviewSnapshotService service = service(request -> diff());

        ReviewSnapshot snapshot = service.generate(
                ReviewTaskId.of(task.getId().value()), ReviewTaskRunId.of(run.getId().value()));

        assertEquals(ReviewAnalysisStatus.SUCCEEDED, snapshot.getAnalysisStatus());
        assertEquals(1, snapshot.getChangeUnits().size());
        assertTrue(snapshot.getEvidence().stream()
                .anyMatch(evidence -> "TASK_RUN".equals(evidence.getSourceType())));
        assertEquals(snapshot.getId(), snapshots.findCurrentByTask(ReviewTaskId.of(1)).orElseThrow().getId());
        assertTrue(events.events.stream().anyMatch(event ->
                event.type().equals("platform.review.requested")));
        assertTrue(events.events.stream().anyMatch(event ->
                event.type().equals("platform.review.diff.reading")));
        assertTrue(events.events.stream().anyMatch(event ->
                event.type().equals("platform.review.analyzing")));
        assertTrue(events.events.stream().anyMatch(event ->
                event.type().equals("platform.review.saving")));
        assertTrue(events.events.stream().anyMatch(event ->
                event.type().equals("platform.review.completed")));
        assertEquals("COMPLETED", service.progress(ReviewTaskId.of(1)).status());
    }

    @Test
    void 同一执行记录重复生成幂等() {
        Task task = completedTask();
        TaskRun run = successfulRun(task);
        ReviewSnapshotService service = service(request -> diff());

        ReviewSnapshot first = service.generate(ReviewTaskId.of(1), ReviewTaskRunId.of(run.getId().value()));
        ReviewSnapshot second = service.generate(ReviewTaskId.of(1), ReviewTaskRunId.of(run.getId().value()));

        assertEquals(first.getId(), second.getId());
        assertEquals(1, snapshots.store.size());
    }

    @Test
    void 重新预审会生成新快照并保留旧快照审计() {
        Task task = completedTask();
        TaskRun run = successfulRun(task);
        ReviewSnapshotService service = service(request -> diff());
        ReviewSnapshot first = service.generate(
                ReviewTaskId.of(1), ReviewTaskRunId.of(run.getId().value()));

        assertTrue(service.requestRegeneration(
                ReviewTaskId.of(1), ReviewTaskRunId.of(run.getId().value())));

        assertEquals(2, snapshots.store.size());
        assertEquals(com.pydlove.regent.review.domain.model.ReviewSnapshotStatus.EXPIRED,
                first.getStatus());
        ReviewSnapshot current = snapshots.findByTaskRun(
                ReviewTaskRunId.of(run.getId().value())).orElseThrow();
        assertTrue(current.getId().value() > first.getId().value());
    }

    @Test
    void 新快照生成时旧快照通过领域方法过期() {
        Task task = completedTask();
        TaskRun firstRun = successfulRun(task);
        TaskRun secondRun = successfulRun(task);
        ReviewSnapshot old = ReviewSnapshot.create(ReviewTaskId.of(1), ReviewTaskRunId.of(firstRun.getId().value()),
                com.pydlove.regent.review.domain.model.ReviewProjectId.of(1), diff(), java.time.Instant.now());
        snapshots.save(old);
        ReviewSnapshotService service = service(request -> diff());

        service.generate(ReviewTaskId.of(1), ReviewTaskRunId.of(secondRun.getId().value()));

        assertEquals(com.pydlove.regent.review.domain.model.ReviewSnapshotStatus.EXPIRED, old.getStatus());
    }

    @Test
    void 非成功执行不能生成快照() {
        Task task = completedTask();
        TaskRun run = TaskRun.start(task.getId(), 1, null, "base");
        runs.save(run);

        assertThrows(DomainException.class,
                () -> service(request -> diff()).generate(
                        ReviewTaskId.of(1), ReviewTaskRunId.of(run.getId().value())));
    }

    @Test
    void 异步分析失败保留原始快照并记录运行事件() {
        Task task = completedTask();
        TaskRun run = successfulRun(task);
        ReviewSnapshotService service = new ReviewSnapshotService(
                snapshots, tasks, runs, projects, events,
                request -> diff(), request -> {
                    throw new IllegalStateException("review unavailable");
                },
                testCases, executor, transactions, java.time.Clock.systemUTC());

        service.scheduleAfterTaskCompleted(ReviewTaskId.of(1), ReviewTaskRunId.of(run.getId().value()));

        ReviewSnapshot snapshot = snapshots.findByTaskRun(ReviewTaskRunId.of(run.getId().value())).orElseThrow();
        assertEquals(ReviewAnalysisStatus.FAILED, snapshot.getAnalysisStatus());
        assertTrue(events.events.stream()
                .anyMatch(event -> event.type().equals("platform.review.analysis.failed")));
    }

    @Test
    void 开放审阅期间后到的测试结果刷新原证据行() {
        Task task = completedTask();
        TaskRun run = successfulRun(task);
        TestCaseRun testRun = testCases.add(task.getId());
        ReviewSnapshotService service = service(request -> diff());
        ReviewSnapshot snapshot = service.generate(ReviewTaskId.of(1), ReviewTaskRunId.of(run.getId().value()));
        assertEquals(com.pydlove.regent.review.domain.model.ReviewEvidenceStatus.NOT_EXECUTED,
                snapshot.getEvidence().stream()
                        .filter(item -> "TEST_CASE_RUN".equals(item.getSourceType()))
                        .findFirst().orElseThrow().getStatus());

        testRun.begin();
        testRun.finish(new TestCaseExecutionResult(TestCaseRunStatus.SUCCESS, 0, "通过", "ok"));
        ReviewSnapshot refreshed = service.current(ReviewTaskId.of(1)).orElseThrow();

        assertEquals(com.pydlove.regent.review.domain.model.ReviewEvidenceStatus.PASSED,
                refreshed.getEvidence().stream()
                        .filter(item -> "TEST_CASE_RUN".equals(item.getSourceType()))
                        .findFirst().orElseThrow().getStatus());
    }

    private Task completedTask() {
        Task task = Task.manual(PROJECT, "审阅任务", "实现变更审阅", 0);
        task.assignIdentity(TASK);
        task.dispatch(null);
        task.complete();
        tasks.add(task);
        return task;
    }

    private TaskRun successfulRun(Task task) {
        TaskRun run = TaskRun.start(task.getId(), runs.all().size() + 1, null, "base-commit");
        runs.save(run);
        run.finish(new ExecResult(RunStatus.SUCCESS, 0, "thread-1", TokenUsage.ZERO,
                "完成", "{}", true, List.of("Task.java"), List.of(), null));
        return run;
    }

    private ReviewSnapshotService service(com.pydlove.regent.review.domain.port.ReviewDiffReader reader) {
        return new ReviewSnapshotService(
                snapshots, tasks, runs, projects, events, reader,
                new RuleBasedReviewAnalyzer(), testCases, executor, transactions,
                java.time.Clock.systemUTC());
    }

    private static ReviewDiff diff() {
        return new ReviewDiff("base-commit", "head-commit", true, HASH,
                List.of(new ReviewDiffFile("Task.java", 2, 1, false, false)),
                2, 1, "diff --git a/Task.java b/Task.java\n", false);
    }

    private static final class InMemorySnapshots implements ReviewSnapshotRepository {

        private final LinkedHashMap<Long, ReviewSnapshot> store = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<ReviewSnapshot> findById(ReviewSnapshotId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<ReviewSnapshot> findByTask(ReviewTaskId taskId) {
            return findCurrentByTask(taskId);
        }

        @Override
        public Optional<ReviewSnapshot> findCurrentByTask(ReviewTaskId taskId) {
            return store.values().stream()
                    .filter(snapshot -> snapshot.getTaskId().equals(taskId))
                    .max(java.util.Comparator.comparingLong(snapshot -> snapshot.getId().value()));
        }

        @Override
        public Optional<ReviewSnapshot> findByTaskRun(ReviewTaskRunId taskRunId) {
            return store.values().stream()
                    .filter(snapshot -> snapshot.getTaskRunId().equals(taskRunId))
                    .max(java.util.Comparator.comparingLong(snapshot -> snapshot.getId().value()));
        }

        @Override
        public ReviewSnapshot save(ReviewSnapshot snapshot) {
            if (snapshot.getId() == null) {
                snapshot.assignIdentity(ReviewSnapshotId.of(++sequence));
            }
            if (snapshot.getChangeUnits().stream().anyMatch(unit -> unit.getId() == null)) {
                for (var unit : snapshot.getChangeUnits()) {
                    if (unit.getId() == null) {
                        unit.assignIdentity(ReviewChangeUnitId.of(++sequence));
                    }
                }
            }
            for (var evidence : snapshot.getEvidence()) {
                if (evidence.getId() == null) {
                    evidence.assignIdentity(ReviewEvidenceId.of(++sequence));
                }
            }
            for (var finding : snapshot.getFindings()) {
                if (finding.getId() == null) {
                    finding.assignIdentity(ReviewFindingId.of(++sequence));
                }
            }
            store.put(snapshot.getId().value(), snapshot);
            return snapshot;
        }

        @Override
        public List<ReviewSnapshot> findUnexpiredByTask(ReviewTaskId taskId) {
            return store.values().stream()
                    .filter(snapshot -> snapshot.getTaskId().equals(taskId))
                    .filter(snapshot -> snapshot.getStatus()
                            != com.pydlove.regent.review.domain.model.ReviewSnapshotStatus.EXPIRED)
                    .toList();
        }
    }

    private static final class Events implements RunEventRepository {

        private final List<RunEvent> events = new ArrayList<>();

        @Override
        public void append(com.pydlove.regent.execution.domain.model.TaskRunId runId, RunEvent event) {
            events.add(event);
        }

        @Override
        public List<RunEvent> findByRun(com.pydlove.regent.execution.domain.model.TaskRunId runId) {
            return List.copyOf(events);
        }
    }

    private static final class TestCaseRuns implements TestCaseRunRepository {
        private final List<TestCaseRun> runs = new ArrayList<>();
        private long sequence;

        private TestCaseRun add(TaskId taskId) {
            TestCaseRun run = TestCaseRun.queueForTask(taskId, PROJECT, new TestCaseBinding(
                    TestCaseId.of(1), "回归测试", TestCaseType.API, "mvn test",
                    null, null, null, true, false, Instant.now()));
            run.assignIdentity(TestCaseRunId.of(++sequence));
            runs.add(run);
            return run;
        }

        @Override public Optional<TestCaseRun> findById(TestCaseRunId id) { return Optional.empty(); }
        @Override public List<TestCaseRun> findByTask(TaskId taskId) {
            return runs.stream().filter(run -> run.getTaskId().orElseThrow().equals(taskId)).toList();
        }
        @Override public List<TestCaseRun> findByTestCase(TestCaseId id, int limit) { return List.of(); }
        @Override public List<TestCaseRun> findLatestByTestCaseIds(List<TestCaseId> ids) { return List.of(); }
        @Override public List<TestCaseRun> findActive() { return List.of(); }
        @Override public boolean existsByTask(TaskId taskId) { return false; }
        @Override public void save(TestCaseRun run) { }
    }

    private static final class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;

        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
        @Override public void execute(Runnable command) { command.run(); }
    }
}
