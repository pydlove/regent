package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.port.BranchManager;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.service.ExecutionConcurrencyGate;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 任务调度 —— 认领任务（同步）并把执行交给工作线程（异步）。
 *
 * <p>拆成「同步认领 + 异步执行」两段是刻意的取舍：
 * <ul>
 *   <li><b>认领必须在请求线程里同步完成</b>：分支切不过去、项目已暂停、状态不允许派发，
 *       这些都应立刻变成用户看到的一句人话，而不是「派发成功」之后在后台默默失败；</li>
 *   <li><b>执行必须在工作线程上</b>：一次真实的 codex 执行是几十分钟的事，
 *       挂在 HTTP 请求上只会收获一个超时。</li>
 * </ul>
 *
 * <p>因此 {@code RUNNING} 的准确含义是「已取得执行许可并被平台认领」。平台级并发闸门
 * 保证同一时刻只有配置数量的任务处于这个状态；真正的「进程在跑」写在
 * {@code task_run.started_at} 上。
 *
 * <h2>四道闸</h2>
 * ① 同一进程内不重复认领（{@code inFlight}）；② 认领失败冷却两分钟，避免调度器每个心跳
 * 都去重试一个必然失败的任务（比如分支名冲突）把日志刷满；③ 项目暂停时不派发新任务，
 * 但不拦已经批准的续跑 —— 暂停的含义是「别再开始新的」。并发闸门在认领前取得，
 * 因而界面里的上限管的是真正开始执行的任务，而不是线程池队列长度。
 */
public class TaskDispatchService {

    /** 认领失败后的冷却时长。失败原因通常不会在两分钟内自己好，重试只是刷日志。 */
    private static final Duration FAILURE_COOLDOWN = Duration.ofMinutes(2);

    private final TaskRepository taskRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final BranchManager branchManager;
    private final BranchProvisioningService branchProvisioningService;
    private final TaskRunService taskRunService;
    private final ExecutorService workers;
    private final ExecutionConcurrencyGate concurrencyGate;
    private final TransactionRunner transactionRunner;

    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<Long, Instant> cooldownUntil = new ConcurrentHashMap<>();

    public TaskDispatchService(TaskRepository taskRepository,
                               ProjectProfileRepository projectProfileRepository,
                               BranchManager branchManager,
                               BranchProvisioningService branchProvisioningService,
                               TaskRunService taskRunService,
                               ExecutorService workers,
                               ExecutionConcurrencyGate concurrencyGate,
                               TransactionRunner transactionRunner) {
        this.taskRepository = taskRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.branchManager = branchManager;
        this.branchProvisioningService = branchProvisioningService;
        this.taskRunService = taskRunService;
        this.workers = workers;
        this.concurrencyGate = concurrencyGate;
        this.transactionRunner = transactionRunner;
    }

    /**
     * 认领一个任务并异步执行；执行位不足时改为排队。返回变更后的任务，调用方据此回显状态。
     *
     * @throws DomainException 状态不允许派发、项目已暂停、分支准备失败
     */
    public Task dispatch(TaskId taskId) {
        return dispatch(taskId, false);
    }

    /**
     * 在调用方事务提交后启动工作线程。
     *
     * <p>跨聚合用例会先在同一个事务里写任务与上游聚合；直接起线程时，工作线程可能拿另一条
     * 连接读不到尚未提交的数据。普通人工派发没有这层上游写入，继续走 {@link #dispatch(TaskId)}。
     */
    public Task dispatchAfterCommit(TaskId taskId) {
        return dispatch(taskId, true);
    }

    private Task dispatch(TaskId taskId, boolean afterCommit) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
        ProjectProfile project = projectProfileRepository.findById(task.getProjectId())
                .orElseThrow(() -> new EntityNotFoundException("任务所属项目不存在：" + task.getProjectId()));

        if (!inFlight.add(taskId.value())) {
            if (task.getStatus() == TaskStatus.QUEUED) {
                return task;
            }
            throw new DomainException("任务 " + taskId + " 正在执行中，不要重复派发");
        }
        Optional<ExecutionConcurrencyGate.Permit> permit = concurrencyGate.tryAcquire();
        if (permit.isEmpty()) {
            inFlight.remove(taskId.value());
            return queue(task, project);
        }
        ExecutionConcurrencyGate.Permit acquired = permit.orElseThrow();
        BranchPreparation preparation;
        try {
            preparation = prepare(task, project);
            taskRepository.save(task);
        } catch (RuntimeException e) {
            acquired.release();
            inFlight.remove(taskId.value());
            throw e;
        }

        // 需要新建分支时只生成了审批单，Git 仍未被触碰；这不是一次执行，不能提交工作线程。
        if (!preparation.ready()) {
            cleanup(taskId, acquired);
            return task;
        }

        try {
            if (afterCommit) {
                transactionRunner.afterCompletion(
                        () -> submit(taskId, acquired),
                        () -> cleanup(taskId, acquired));
            } else {
                submit(taskId, acquired);
            }
        } catch (RuntimeException e) {
            cleanup(taskId, acquired);
            throw e;
        }
        return task;
    }

    private void submit(TaskId taskId, ExecutionConcurrencyGate.Permit acquired) {
        try {
            workers.execute(() -> {
                try {
                    taskRunService.execute(taskId);
                } finally {
                    cleanup(taskId, acquired);
                    drainQueuedTask();
                }
            });
        } catch (RuntimeException e) {
            cleanup(taskId, acquired);
            throw e;
        }
    }

    private void cleanup(TaskId taskId, ExecutionConcurrencyGate.Permit acquired) {
        acquired.release();
        inFlight.remove(taskId.value());
    }

    /**
     * 逐个派发一组任务。
     *
     * <p>顺序处理而不是并发提交：同一个项目的分支准备会操作同一个工作区，批量并发切分支
     * 更容易互相踩踏。重复 ID 先去重，单项失败也只记录该任务并继续后面的任务。
     */
    public TaskDispatchBatchResult dispatchBatch(List<TaskId> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个任务");
        }
        LinkedHashSet<TaskId> uniqueTaskIds = new LinkedHashSet<>(taskIds);
        if (uniqueTaskIds.contains(null)) {
            throw new IllegalArgumentException("任务 ID 不能为空");
        }

        List<TaskId> dispatched = new ArrayList<>();
        List<TaskDispatchBatchResult.Failure> failures = new ArrayList<>();
        for (TaskId taskId : uniqueTaskIds) {
            try {
                dispatch(taskId);
                dispatched.add(taskId);
            } catch (RuntimeException e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                failures.add(new TaskDispatchBatchResult.Failure(taskId, message));
            }
        }
        return new TaskDispatchBatchResult(dispatched, failures);
    }

    /**
     * 定时扫描：把库里可派发的任务领起来，返回本次认领的数量。
     *
     * <p>单个任务认领失败只影响它自己 —— 一个项目暂停、一条分支切不过去，
     * 都不该让排在后面的任务跟着停摆。
     */
    public int dispatchCandidates(int limit) {
        List<Task> candidates = taskRepository.findDispatchable(limit);
        int claimed = 0;
        for (Task candidate : candidates) {
            if (!concurrencyGate.hasAvailableSlot()) {
                break;
            }
            TaskId taskId = candidate.getId();
            if (inFlight.contains(taskId.value()) || isCoolingDown(taskId)) {
                continue;
            }
            try {
                dispatch(taskId);
                claimed++;
            } catch (RuntimeException e) {
                if (!concurrencyGate.hasAvailableSlot()) {
                    break;
                }
                cooldown(taskId);
            }
        }
        return claimed;
    }

    /** 认领：把任务状态推进到「已派发」，并把分支准备好；需要审批时只挂起，不创建分支。 */
    private BranchPreparation prepare(Task task, ProjectProfile project) {
        if (task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.QUEUED) {
            project.requireDispatchable();
            return branchProvisioningService.prepare(task, project);
        }
        if (task.getStatus() == TaskStatus.RUNNING) {
            // 续跑：批准之后工作区可能已经切到别的分支上了，先切回去再接着跑
            branchManager.switchTo(project.path(), task.getBranchName().orElseThrow(
                    () -> new DomainException("任务没有分支名，无法续跑：" + task.getId())));
            task.beginContinuation();
            return BranchPreparation.started(task.getBranchName().orElseThrow());
        }
        throw new DomainException("任务当前状态不可派发：" + task.getId() + "，状态：" + task.getStatus());
    }

    /**
     * 没有空闲执行位时把首次执行请求落成 QUEUED。它不是一次执行尝试，
     * 因而不占并发许可；调度扫描或前一个任务收场后会再取出来起跑。
     */
    private Task queue(Task task, ProjectProfile project) {
        if (task.getStatus() == TaskStatus.QUEUED) {
            return task;
        }
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new DomainException("任务当前状态不可排队：" + task.getId() + "，状态：" + task.getStatus());
        }
        project.requireDispatchable();
        task.queue();
        taskRepository.save(task);
        return task;
    }

    /** 每释放一个执行位就补一个用户明确排队的任务；PENDING 仍只由自动派发开关控制。 */
    private void drainQueuedTask() {
        if (!concurrencyGate.hasAvailableSlot()) {
            return;
        }
        List<Task> queued = taskRepository.findQueued(1);
        if (queued.isEmpty()) {
            return;
        }
        TaskId taskId = queued.get(0).getId();
        if (isCoolingDown(taskId)) {
            return;
        }
        try {
            dispatch(taskId);
        } catch (RuntimeException e) {
            cooldown(taskId);
        }
    }

    private boolean isCoolingDown(TaskId taskId) {
        return cooldownUntil.getOrDefault(taskId.value(), Instant.EPOCH).isAfter(now());
    }

    private void cooldown(TaskId taskId) {
        cooldownUntil.put(taskId.value(), now().plus(FAILURE_COOLDOWN));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
