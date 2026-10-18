package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 平台启动时的执行恢复。
 *
 * <p>内部 codex 进程随平台进程一起消失，但数据库里的 {@code task_run} 和 {@code task}
 * 仍可能停在 {@code RUNNING}。如果不处理，下一次扫描永远不会认领普通 RUNNING 任务，
 * 工作台上就会出现一条永远不动的「执行中」。
 *
 * <p>恢复分两步，且都在事务内完成：
 * <ol>
 *   <li>把所有平台内部执行留下的 RUNNING 记录收敛为 {@code INTERRUPTED}；</li>
 *   <li>核对仍为 RUNNING 的任务。等决策的任务保持不动；外部执行者有租约，也保持不动；
 *       其余无人接管的任务回到 {@code QUEUED}，由正常派发链路创建下一次尝试。</li>
 * </ol>
 *
 * <p>本服务只排队，不直接启动 codex。这样分支审批、并发闸门和指令拼装仍然只有一条路径。
 */
public class ExecutionRecoveryService {

    public static final String RESTART_INTERRUPT_REASON =
            "平台重启，原执行进程已退出";

    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final TransactionRunner transactionRunner;

    public ExecutionRecoveryService(TaskRepository taskRepository,
                                    TaskRunRepository taskRunRepository,
                                    TransactionRunner transactionRunner) {
        this.taskRepository = taskRepository;
        this.taskRunRepository = taskRunRepository;
        this.transactionRunner = transactionRunner;
    }

    /**
     * 恢复所有可恢复的内部执行。重复调用是幂等的：已经收敛的记录不会再被改动，
     * 已经离开 RUNNING 的任务也不会再次排队。
     */
    public ExecutionRecoveryResult recoverInterruptedExecutions() {
        Map<TaskId, List<TaskRun>> staleByTask = new LinkedHashMap<>();
        for (TaskRun run : taskRunRepository.findRunningInternal()) {
            staleByTask.computeIfAbsent(run.getTaskId(), ignored -> new ArrayList<>()).add(run);
        }

        Set<TaskId> taskIds = new LinkedHashSet<>(staleByTask.keySet());
        taskRepository.findRunning().forEach(task -> taskIds.add(task.getId()));

        int interruptedRuns = 0;
        int requeuedTasks = 0;
        List<Failure> failures = new ArrayList<>();
        for (TaskId taskId : taskIds) {
            try {
                TaskRecoveryOutcome outcome = transactionRunner.inTransaction(
                        () -> recoverTask(taskId, staleByTask.getOrDefault(taskId, List.of())));
                interruptedRuns += outcome.interruptedRuns();
                requeuedTasks += outcome.requeued() ? 1 : 0;
            } catch (RuntimeException e) {
                failures.add(new Failure(taskId, messageOf(e)));
            }
        }
        return new ExecutionRecoveryResult(interruptedRuns, requeuedTasks, failures);
    }

    private TaskRecoveryOutcome recoverTask(TaskId taskId, List<TaskRun> staleRuns) {
        int interruptedRuns = interruptStaleRuns(staleRuns);
        Task task = taskRepository.findByIdForUpdate(taskId).orElse(null);
        if (task == null || task.getStatus() != TaskStatus.RUNNING) {
            return new TaskRecoveryOutcome(interruptedRuns, false);
        }

        TaskRun latest = taskRunRepository.findLatestByTask(taskId).orElse(null);
        if (latest == null) {
            task.requeueAfterInterruption();
            taskRepository.save(task);
            return new TaskRecoveryOutcome(interruptedRuns, true);
        }

        // 决策已批准、只差继续跑的任务由派发扫描正常接管；它不是在重启中丢失的执行。
        if (latest.getStatus() == RunStatus.BLOCKED) {
            return new TaskRecoveryOutcome(interruptedRuns, false);
        }
        // 外部执行者跑在平台之外，是否有活性由租约判定，平台重启不能替它下结论。
        if (!TaskRun.EXECUTOR_INTERNAL.equals(latest.getExecutor())) {
            return new TaskRecoveryOutcome(interruptedRuns, false);
        }

        if (latest.getStatus() == RunStatus.RUNNING) {
            latest.interrupt(RESTART_INTERRUPT_REASON);
            taskRunRepository.save(latest);
            interruptedRuns++;
        }

        task.requeueAfterInterruption();
        taskRepository.save(task);
        return new TaskRecoveryOutcome(interruptedRuns, true);
    }

    private int interruptStaleRuns(List<TaskRun> staleRuns) {
        int interrupted = 0;
        for (TaskRun stale : staleRuns) {
            TaskRun fresh = taskRunRepository.findById(stale.getId()).orElse(null);
            if (fresh == null || fresh.getStatus() != RunStatus.RUNNING) {
                continue;
            }
            fresh.interrupt(RESTART_INTERRUPT_REASON);
            taskRunRepository.save(fresh);
            interrupted++;
        }
        return interrupted;
    }

    private static String messageOf(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private record TaskRecoveryOutcome(int interruptedRuns, boolean requeued) {
    }

    /** 一次启动恢复的结果，供入口统一写日志。 */
    public record ExecutionRecoveryResult(int interruptedRuns,
                                          int requeuedTasks,
                                          List<Failure> failures) {

        public ExecutionRecoveryResult {
            failures = List.copyOf(failures);
        }

        public boolean hasChanges() {
            return interruptedRuns > 0 || requeuedTasks > 0;
        }
    }

    public record Failure(TaskId taskId, String message) {
    }
}
