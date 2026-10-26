package com.pydlove.regent.review.application.command;

import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffRequest;
import com.pydlove.regent.review.domain.model.ReviewAnalysis;
import com.pydlove.regent.review.domain.model.ReviewAnalysisRequest;
import com.pydlove.regent.review.domain.model.ReviewEvidence;
import com.pydlove.regent.review.domain.model.ReviewEvidenceStatus;
import com.pydlove.regent.review.domain.model.ReviewEvidenceType;
import com.pydlove.regent.review.domain.model.ReviewProjectId;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewTaskRunId;
import com.pydlove.regent.review.domain.port.ReviewDiffReader;
import com.pydlove.regent.review.domain.port.ReviewAnalyzer;
import com.pydlove.regent.review.domain.port.ReviewSnapshotScheduler;
import com.pydlove.regent.review.domain.repository.ReviewSnapshotRepository;
import com.pydlove.regent.review.application.query.ReviewGenerationProgress;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.testing.domain.model.TestCaseRun;
import com.pydlove.regent.testing.domain.model.TestCaseRunStatus;
import com.pydlove.regent.testing.domain.repository.TestCaseRunRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 任务成功收场后的审阅快照生成。
 *
 * <p>代码事实与审阅状态都落库；异步线程失败不会反噬任务状态，但会在运行事件流留下
 * {@code platform.review.failed}，用户仍能手工重试生成。
 */
public class ReviewSnapshotService implements ReviewSnapshotScheduler {

    private final ReviewSnapshotRepository snapshotRepository;
    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final RunEventRepository runEventRepository;
    private final ReviewDiffReader diffReader;
    private final ReviewAnalyzer analyzer;
    private final TestCaseRunRepository testCaseRunRepository;
    private final ExecutorService workers;
    private final TransactionRunner transactionRunner;
    private final Clock clock;
    private final Set<Long> activeRuns = ConcurrentHashMap.newKeySet();

    public ReviewSnapshotService(ReviewSnapshotRepository snapshotRepository,
                                 TaskRepository taskRepository,
                                 TaskRunRepository taskRunRepository,
                                 ProjectProfileRepository projectProfileRepository,
                                 RunEventRepository runEventRepository,
                                 ReviewDiffReader diffReader,
                                 ReviewAnalyzer analyzer,
                                 TestCaseRunRepository testCaseRunRepository,
                                 ExecutorService workers,
                                 TransactionRunner transactionRunner) {
        this(snapshotRepository, taskRepository, taskRunRepository, projectProfileRepository,
                runEventRepository, diffReader, analyzer, testCaseRunRepository,
                workers, transactionRunner, Clock.systemUTC());
    }

    ReviewSnapshotService(ReviewSnapshotRepository snapshotRepository,
                          TaskRepository taskRepository,
                          TaskRunRepository taskRunRepository,
                          ProjectProfileRepository projectProfileRepository,
                          RunEventRepository runEventRepository,
                          ReviewDiffReader diffReader,
                          ReviewAnalyzer analyzer,
                          TestCaseRunRepository testCaseRunRepository,
                          ExecutorService workers,
                          TransactionRunner transactionRunner,
                          Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.taskRepository = taskRepository;
        this.taskRunRepository = taskRunRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.runEventRepository = runEventRepository;
        this.diffReader = diffReader;
        this.analyzer = analyzer;
        this.testCaseRunRepository = testCaseRunRepository;
        this.workers = workers;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    @Override
    public void scheduleAfterTaskCompleted(ReviewTaskId taskId, ReviewTaskRunId taskRunId) {
        requestGeneration(taskId, taskRunId);
    }

    /**
     * 手动触发时立即返回，真正的耗时工作交给审阅线程池；前端通过 {@link #progress} 轮询。
     */
    public boolean requestGeneration(ReviewTaskId taskId, ReviewTaskRunId taskRunId) {
        return requestGeneration(taskId, taskRunId, true);
    }

    /** 重新生成同一执行记录的快照；旧快照保留，并在新快照落库时过期。 */
    public boolean requestRegeneration(ReviewTaskId taskId, ReviewTaskRunId taskRunId) {
        return requestGeneration(taskId, taskRunId, false);
    }

    private boolean requestGeneration(ReviewTaskId taskId,
                                      ReviewTaskRunId taskRunId,
                                      boolean reuseExisting) {
        if (reuseExisting && snapshotRepository.findByTaskRun(taskRunId).isPresent()) {
            return false;
        }
        if (!activeRuns.add(taskRunId.value())) {
            return false;
        }
        appendProgress(taskRunId, "platform.review.requested", "queued", "已进入审阅队列");
        workers.execute(() -> {
            try {
                generateInternal(taskId, taskRunId, false, reuseExisting);
            } catch (RuntimeException failure) {
                appendFailureEvent(taskRunId, failure);
            } finally {
                activeRuns.remove(taskRunId.value());
            }
        });
        return true;
    }

    /** 手工补生成接口用：失败会直接反馈给调用方。 */
    public ReviewSnapshot generate(ReviewTaskId taskId, ReviewTaskRunId taskRunId) {
        return generateInternal(taskId, taskRunId, true, true);
    }

    private ReviewSnapshot generateInternal(ReviewTaskId taskId,
                                            ReviewTaskRunId taskRunId,
                                            boolean announce,
                                            boolean reuseExisting) {
        if (reuseExisting) {
            Optional<ReviewSnapshot> existing = snapshotRepository.findByTaskRun(taskRunId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        if (announce) {
            appendProgress(taskRunId, "platform.review.requested", "queued", "已进入审阅队列");
        }

        Task task = taskRepository.findById(TaskId.of(taskId.value()))
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId.value()));
        TaskRun run = taskRunRepository.findById(TaskRunId.of(taskRunId.value()))
                .orElseThrow(() -> new EntityNotFoundException("执行记录不存在：" + taskRunId.value()));
        if (!run.getTaskId().equals(task.getId())) {
            throw new DomainException("执行记录不属于该任务");
        }
        if (run.getStatus() != RunStatus.SUCCESS) {
            throw new DomainException("只有成功收场的执行记录可以生成审阅快照");
        }
        ProjectProfile project = projectProfileRepository.findById(task.getProjectId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "任务所属项目不存在：" + task.getProjectId().value()));

        appendProgress(taskRunId, "platform.review.diff.reading", "diff", "正在读取代码变更");
        ReviewDiff diff = diffReader.read(new ReviewDiffRequest(
                project.path(), run.getBaseRef(), run.getChangedFiles()));
        ReviewSnapshot candidate = ReviewSnapshot.create(taskId, taskRunId,
                ReviewProjectId.of(project.id().value()), diff, now());
        try {
            appendProgress(taskRunId, "platform.review.analyzing", "analysis", "AI 正在预审变更");
            ReviewAnalysis analysis = analyzer.analyze(new ReviewAnalysisRequest(
                    taskId, project.path(), task.getTitle(), task.getPrompt(), diff, evidenceFor(task, run)));
            candidate.attachAnalysis(analysis, now());
        } catch (RuntimeException e) {
            candidate.failAnalysis(safeFailureMessage(e), now());
            appendProgress(taskRunId, "platform.review.analysis.failed", "fallback",
                    "AI 预审不可用，已采用规则预审");
        }

        appendProgress(taskRunId, "platform.review.saving", "saving", "正在保存审阅结果");
        return transactionRunner.inTransaction(() -> {
            if (reuseExisting) {
                Optional<ReviewSnapshot> concurrent = snapshotRepository.findByTaskRun(taskRunId);
                if (concurrent.isPresent()) {
                    return concurrent.get();
                }
            }
            for (ReviewSnapshot old : snapshotRepository.findUnexpiredByTask(taskId)) {
                old.expire(now());
                snapshotRepository.save(old);
            }
            ReviewSnapshot saved = snapshotRepository.save(candidate);
            appendProgress(taskRunId, "platform.review.completed", "completed", "审阅结果已生成");
            return saved;
        });
    }

    public ReviewGenerationProgress progress(ReviewTaskId taskId) {
        TaskRun latestRun = latestSuccessfulRun(taskId).orElse(null);
        if (latestRun != null && activeRuns.contains(latestRun.getId().value())) {
            return progressFromEvents(latestRun);
        }
        Optional<ReviewSnapshot> snapshot = snapshotRepository.findCurrentByTask(taskId);
        if (snapshot.isPresent()) {
            return new ReviewGenerationProgress(
                    "COMPLETED", "completed", "审阅结果已生成",
                    snapshot.get().getUpdatedAt(), null);
        }
        if (latestRun == null) {
            return ReviewGenerationProgress.idle();
        }
        return progressFromEvents(latestRun);
    }

    private ReviewGenerationProgress progressFromEvents(TaskRun run) {
        List<RunEvent> reviewEvents = runEventRepository.findByRun(run.getId()).stream()
                .filter(event -> event.type().startsWith("platform.review."))
                .toList();
        if (reviewEvents.isEmpty()) {
            if (activeRuns.contains(run.getId().value())) {
                return new ReviewGenerationProgress(
                        "QUEUED", "queued", "已进入审阅队列", now(), null);
            }
            return ReviewGenerationProgress.idle();
        }
        RunEvent last = reviewEvents.get(reviewEvents.size() - 1);
        ReviewGenerationProgress progress = switch (last.type()) {
            case "platform.review.requested" -> new ReviewGenerationProgress(
                    "QUEUED", "queued", "已进入审阅队列", last.occurredAt(), null);
            case "platform.review.diff.reading" -> new ReviewGenerationProgress(
                    "RUNNING", "diff", "正在读取代码变更", last.occurredAt(), null);
            case "platform.review.analyzing" -> new ReviewGenerationProgress(
                    "RUNNING", "analysis", "AI 正在预审变更", last.occurredAt(), null);
            case "platform.review.analysis.failed" -> new ReviewGenerationProgress(
                    "RUNNING", "fallback", "AI 预审不可用，已采用规则预审",
                    last.occurredAt(), null);
            case "platform.review.saving" -> new ReviewGenerationProgress(
                    "RUNNING", "saving", "正在保存审阅结果", last.occurredAt(), null);
            case "platform.review.completed" -> new ReviewGenerationProgress(
                    "COMPLETED", "completed", "审阅结果已生成", last.occurredAt(), null);
            case "platform.review.failed" -> new ReviewGenerationProgress(
                    "FAILED", "failed", "审阅生成失败，请重试", last.occurredAt(),
                    "生成过程异常");
            default -> ReviewGenerationProgress.idle();
        };
        if (("RUNNING".equals(progress.status()) || "QUEUED".equals(progress.status()))
                && progress.updatedAt() != null
                && progress.updatedAt().isBefore(now().minus(Duration.ofMinutes(3)))) {
            return new ReviewGenerationProgress(
                    "FAILED", "timeout", "审阅生成超时，请重试",
                    progress.updatedAt(), "超过 3 分钟没有新进度");
        }
        return progress;
    }

    public Optional<ReviewSnapshot> current(ReviewTaskId taskId) {
        Optional<ReviewSnapshot> found = snapshotRepository.findCurrentByTask(taskId);
        if (found.isPresent() && found.get().getStatus().isOpen()) {
            refreshTestEvidence(found.get());
        }
        if (found.isEmpty() || found.get().getStatus()
                != com.pydlove.regent.review.domain.model.ReviewSnapshotStatus.APPROVED) {
            return found;
        }
        ReviewSnapshot snapshot = found.get();
        TaskRun run = taskRunRepository.findById(TaskRunId.of(snapshot.getTaskRunId().value()))
                .orElseThrow(() -> new EntityNotFoundException("审阅快照关联的执行记录不存在"));
        ProjectProfile project = projectProfileRepository.findById(
                        com.pydlove.regent.project.domain.model.ProjectId.of(snapshot.getProjectId().value()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "审阅快照关联的项目不存在：" + snapshot.getProjectId().value()));
        ReviewDiff current = diffReader.read(new ReviewDiffRequest(
                project.path(), snapshot.getBaseRef(), run.getChangedFiles()));
        if (snapshot.matchesDiffHash(current.diffHash())) {
            return found;
        }
        transactionRunner.inTransaction(() -> {
            ReviewSnapshot fresh = snapshotRepository.findCurrentByTask(taskId).orElse(snapshot);
            fresh.expire(now());
            snapshotRepository.save(fresh);
            return null;
        });
        return snapshotRepository.findCurrentByTask(taskId);
    }

    private void refreshTestEvidence(ReviewSnapshot snapshot) {
        Task task = taskRepository.findById(TaskId.of(snapshot.getTaskId().value())).orElse(null);
        if (task == null) {
            return;
        }
        List<ReviewEvidence> refreshed = testEvidenceFor(task);
        if (snapshot.refreshEvidence(refreshed, now())) {
            transactionRunner.inTransaction(() -> {
                snapshotRepository.save(snapshot);
                return null;
            });
        }
    }

    private Optional<TaskRun> latestSuccessfulRun(ReviewTaskId taskId) {
        return taskRunRepository.listByTask(TaskId.of(taskId.value())).stream()
                .filter(run -> run.getStatus() == RunStatus.SUCCESS)
                .max(java.util.Comparator.comparingInt(TaskRun::getAttemptNo));
    }

    private void appendFailureEvent(ReviewTaskRunId taskRunId, RuntimeException failure) {
        appendEvent(taskRunId, "platform.review.failed",
                "{\"error\":\"" + failure.getClass().getSimpleName() + "\"}");
    }

    private void appendProgress(ReviewTaskRunId taskRunId,
                                String type,
                                String stage,
                                String message) {
        appendEvent(taskRunId, type,
                "{\"stage\":\"" + stage + "\",\"message\":\"" + message + "\"}");
    }

    private void appendEvent(ReviewTaskRunId taskRunId, String type, String payload) {
        try {
            int nextSeq = runEventRepository.findByRun(TaskRunId.of(taskRunId.value())).stream()
                    .mapToInt(RunEvent::seq)
                    .max()
                    .orElse(-1) + 1;
            runEventRepository.append(TaskRunId.of(taskRunId.value()),
                    new RunEvent(nextSeq, type, payload, now()));
        } catch (RuntimeException ignored) {
            // 审阅是收场后的旁路；事件流也不可用时不能把工作线程拖垮。
        }
    }

    private List<ReviewEvidence> evidenceFor(Task task, TaskRun run) {
        Instant occurredAt = run.getFinishedAt() == null ? now() : run.getFinishedAt();
        List<ReviewEvidence> evidence = new java.util.ArrayList<>();
        evidence.add(ReviewEvidence.create(
                null,
                ReviewEvidenceType.LOG,
                ReviewEvidenceStatus.PASSED,
                "任务执行成功",
                redact(run.getExecutor()),
                redact(run.getSummary() == null ? "执行器报告成功完成" : run.getSummary()),
                "TASK_RUN",
                run.getId().value(),
                occurredAt));
        evidence.addAll(testEvidenceFor(task));
        return List.copyOf(evidence);
    }

    private List<ReviewEvidence> testEvidenceFor(Task task) {
        List<ReviewEvidence> evidence = new java.util.ArrayList<>();
        for (TestCaseRun testRun : testCaseRunRepository.findByTask(task.getId())) {
            evidence.add(ReviewEvidence.create(
                    null,
                    ReviewEvidenceType.TEST,
                    evidenceStatus(testRun.getStatus()),
                    "测试：" + testRun.getTestCaseName(),
                    redact(testRun.getCommand() == null
                            ? testRun.getScriptFilename() : testRun.getCommand()),
                    redact(testRun.getSummary()),
                    "TEST_CASE_RUN",
                    testRun.getId().value(),
                    testRun.getFinishedAt() == null ? testRun.getQueuedAt() : testRun.getFinishedAt()));
        }
        return List.copyOf(evidence);
    }

    private static ReviewEvidenceStatus evidenceStatus(TestCaseRunStatus status) {
        return switch (status) {
            case SUCCESS -> ReviewEvidenceStatus.PASSED;
            case FAILED, TIMEOUT, INTERRUPTED -> ReviewEvidenceStatus.FAILED;
            case QUEUED, RUNNING, SKIPPED -> ReviewEvidenceStatus.NOT_EXECUTED;
        };
    }

    private static String safeFailureMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        String redacted = redact(message);
        return redacted.length() <= 1000 ? redacted : redacted.substring(0, 1000);
    }

    private static String redact(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("(?i)(password|passwd|secret|token|api[_-]?key|private[_-]?key)"
                        + "(\\s*[:=]\\s*)([^\\s\"']+)", "$1$2***")
                .replaceAll("\\bsk-[A-Za-z0-9_-]{8,}\\b", "sk-***");
    }

    private Instant now() {
        return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }
}
