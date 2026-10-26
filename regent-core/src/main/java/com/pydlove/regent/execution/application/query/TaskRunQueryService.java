package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.ArrayList;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 执行记录查询。
 *
 * <p>不另开读模型：一个任务的尝试次数是两位数，详情页要的字段与聚合完全重合。
 * 为它造一套查询对象与投影，只会多出一处需要同步维护的字段列表。
 */
public class TaskRunQueryService {

    private static final Duration SLOW_AFTER = Duration.ofSeconds(15);
    private static final Duration STALLED_AFTER = Duration.ofSeconds(30);
    private static final int MAX_DIAGNOSTIC_SUMMARY_CHARS = 2000;

    private final TaskRunRepository taskRunRepository;
    private final RunEventRepository runEventRepository;
    private final Clock clock;

    public TaskRunQueryService(TaskRunRepository taskRunRepository, RunEventRepository runEventRepository) {
        this(taskRunRepository, runEventRepository, Clock.systemUTC());
    }

    public TaskRunQueryService(TaskRunRepository taskRunRepository,
                               RunEventRepository runEventRepository,
                               Clock clock) {
        this.taskRunRepository = taskRunRepository;
        this.runEventRepository = runEventRepository;
        this.clock = clock;
    }

    /** 某个任务的全部尝试，最新的在前 —— 看日志的人关心的永远是最近一次。 */
    public List<TaskRun> listByTask(TaskId taskId) {
        List<TaskRun> runs = new ArrayList<>(taskRunRepository.listByTask(taskId));
        Collections.reverse(runs);
        return List.copyOf(runs);
    }

    /** 任务最近一次成功收场；审阅快照只允许绑定这条执行事实。 */
    public TaskRun latestSuccess(TaskId taskId) {
        return taskRunRepository.listByTask(taskId).stream()
                .filter(run -> run.getStatus() == RunStatus.SUCCESS)
                .max(java.util.Comparator.comparingInt(TaskRun::getAttemptNo))
                .orElseThrow(() -> new EntityNotFoundException(
                        "任务还没有成功收场的执行记录：" + taskId));
    }

    public TaskRun get(TaskRunId id) {
        return taskRunRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("执行记录不存在：" + id));
    }

    public TaskRun get(TaskRunId id, UserId ownerId) {
        return taskRunRepository.findByIdOwned(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("执行记录不存在：" + id));
    }

    /** 事件流按序号升序 —— 它就是「当时发生了什么」的时间线，不能倒着读。 */
    public List<RunEvent> events(TaskRunId runId) {
        return runEventRepository.findByRun(runId);
    }

    /** 为用户时间线与失败卡提供同一份诊断事实。 */
    public ExecutionDiagnostic diagnose(TaskRunId runId) {
        TaskRun run = get(runId);
        List<RunEvent> events = runEventRepository.findByRun(runId);
        String text = normalized(run.getSummary());
        String failureCategory = failureCategory(run, text);
        boolean failed = run.getStatus().isFailure() || run.getStatus() == RunStatus.INTERRUPTED;
        boolean deviceLost = "DEVICE_LOST".equals(failureCategory);
        return new ExecutionDiagnostic(
                run.getId(),
                run.getStatus(),
                health(run),
                failureCategory,
                lastSuccessStage(run, events),
                failureStage(events, failed),
                retryable(failureCategory, failed),
                deviceLost || "CODEX_UNAVAILABLE".equals(failureCategory),
                run.getStatus() == RunStatus.BLOCKED,
                redact(run.getSummary()),
                lastActivity(run));
    }

    private String health(TaskRun run) {
        if (run.getStatus().isFinished()) {
            if (run.getStatus() == RunStatus.SUCCESS || run.getStatus() == RunStatus.BLOCKED) {
                return "HEALTHY";
            }
            return run.getStatus() == RunStatus.INTERRUPTED ? "UNKNOWN" : "FAILED";
        }
        Instant now = clock.instant();
        if (run.isLeaseExpiredAt(now)) {
            return "DEVICE_LOST";
        }
        Instant activity = lastActivity(run);
        if (activity == null) {
            return "UNKNOWN";
        }
        Duration age = Duration.between(activity, now);
        if (age.compareTo(STALLED_AFTER) >= 0) {
            return "STALLED";
        }
        if (age.compareTo(SLOW_AFTER) >= 0) {
            return "SLOW";
        }
        return "HEALTHY";
    }

    private static Instant lastActivity(TaskRun run) {
        if (run.getLastEventAt() != null) {
            return run.getLastEventAt();
        }
        if (run.getStartedAt() != null) {
            return run.getStartedAt();
        }
        return run.getLastHeartbeatAt();
    }

    private String lastSuccessStage(TaskRun run, List<RunEvent> events) {
        boolean priorSuccess = taskRunRepository.listByTask(run.getTaskId()).stream()
                .filter(candidate -> candidate.getAttemptNo() < run.getAttemptNo())
                .anyMatch(candidate -> candidate.getStatus() == RunStatus.SUCCESS);
        if (priorSuccess || run.getStatus() == RunStatus.SUCCESS) {
            return "执行完成";
        }
        if (hasEvent(events, "thread.started")) {
            return "已建立执行会话";
        }
        if (hasEvent(events, "agent.claim")) {
            return "已领取任务";
        }
        return "尚未开始";
    }

    private static String failureStage(List<RunEvent> events, boolean failed) {
        if (!failed) {
            return null;
        }
        if (hasEvent(events, "agent.report") || hasEvent(events, "turn.completed")) {
            return "执行结果上报";
        }
        if (hasEvent(events, "thread.started")) {
            return "执行会话";
        }
        if (hasEvent(events, "agent.claim")) {
            return "任务领取";
        }
        return "环境检查";
    }

    private static String failureCategory(TaskRun run, String text) {
        if (!run.getStatus().isFailure() && run.getStatus() != RunStatus.INTERRUPTED) {
            return null;
        }
        if (run.getStatus() == RunStatus.TIMEOUT || text.contains("timeout") || text.contains("超时")) {
            return "TIMEOUT";
        }
        if (!run.isSchemaValid()) {
            return "INVALID_OUTPUT";
        }
        if (text.contains("失联") || text.contains("lease expired")) {
            return "DEVICE_LOST";
        }
        if (text.contains("codex") && (text.contains("未安装") || text.contains("不可用")
                || text.contains("not found") || text.contains("未找到"))) {
            return "CODEX_UNAVAILABLE";
        }
        if (text.contains("项目目录") || text.contains("工作区") || text.contains("workspace")
                || text.contains("directory")) {
            return "WORKSPACE_UNAVAILABLE";
        }
        if (text.contains("登录") || text.contains("认证") || text.contains("token")) {
            return "AUTHENTICATION";
        }
        return "EXECUTOR_FAILURE";
    }

    private static boolean retryable(String category, boolean failed) {
        if (!failed) {
            return false;
        }
        return category != null && !"INVALID_OUTPUT".equals(category);
    }

    private static boolean hasEvent(List<RunEvent> events, String type) {
        return events.stream().anyMatch(event -> type.equals(event.type()));
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String redact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String redacted = value
                .replaceAll("(?i)(password|passwd|token|secret|authorization)(\\s*[:=]\\s*)\\S+",
                        "$1$2***")
                .replaceAll("\\bsk-[A-Za-z0-9_-]{8,}\\b", "sk-***");
        return redacted.length() <= MAX_DIAGNOSTIC_SUMMARY_CHARS
                ? redacted
                : redacted.substring(0, MAX_DIAGNOSTIC_SUMMARY_CHARS) + "…";
    }
}
