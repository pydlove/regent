package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.port.BranchManager;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
 * <p>因此 {@code RUNNING} 的准确含义是「已被平台认领」：可能正在排在工作队列里。
 * 真正的「进程在跑」写在 {@code task_run.started_at} 上。
 *
 * <h2>三道闸</h2>
 * ① 同一进程内不重复认领（{@code inFlight}）；② 认领失败冷却两分钟，避免调度器每个心跳
 * 都去重试一个必然失败的任务（比如分支名冲突）把日志刷满；③ 项目暂停时不派发新任务，
 * 但不拦已经批准的续跑 —— 暂停的含义是「别再开始新的」。
 */
public class TaskDispatchService {

    /** 认领失败后的冷却时长。失败原因通常不会在两分钟内自己好，重试只是刷日志。 */
    private static final Duration FAILURE_COOLDOWN = Duration.ofMinutes(2);

    private final TaskRepository taskRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final BranchManager branchManager;
    private final TaskRunService taskRunService;
    private final ExecutorService workers;

    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<Long, Instant> cooldownUntil = new ConcurrentHashMap<>();

    public TaskDispatchService(TaskRepository taskRepository,
                               ProjectProfileRepository projectProfileRepository,
                               BranchManager branchManager,
                               TaskRunService taskRunService,
                               ExecutorService workers) {
        this.taskRepository = taskRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.branchManager = branchManager;
        this.taskRunService = taskRunService;
        this.workers = workers;
    }

    /**
     * 认领一个任务并异步执行。返回认领后的任务，调用方据此立刻回显状态。
     *
     * @throws DomainException 状态不允许派发、项目已暂停、分支准备失败
     */
    public Task dispatch(TaskId taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
        ProjectProfile project = projectProfileRepository.findById(task.getProjectId())
                .orElseThrow(() -> new EntityNotFoundException("任务所属项目不存在：" + task.getProjectId()));

        if (!inFlight.add(taskId.value())) {
            throw new DomainException("任务 " + taskId + " 正在执行中，不要重复派发");
        }
        try {
            prepare(task, project);
            taskRepository.save(task);
        } catch (RuntimeException e) {
            inFlight.remove(taskId.value());
            throw e;
        }

        workers.execute(() -> {
            try {
                taskRunService.execute(taskId);
            } finally {
                inFlight.remove(taskId.value());
            }
        });
        return task;
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
            TaskId taskId = candidate.getId();
            if (inFlight.contains(taskId.value()) || isCoolingDown(taskId)) {
                continue;
            }
            try {
                dispatch(taskId);
                claimed++;
            } catch (RuntimeException e) {
                cooldown(taskId);
            }
        }
        return claimed;
    }

    /** 认领：把任务状态推进到「已派发」，并把分支准备好。 */
    private void prepare(Task task, ProjectProfile project) {
        if (task.getStatus() == TaskStatus.PENDING) {
            project.requireDispatchable();
            task.dispatch(branchManager.createFor(project.path(), task.getId()));
            return;
        }
        if (task.getStatus() == TaskStatus.RUNNING) {
            // 续跑：批准之后工作区可能已经切到别的分支上了，先切回去再接着跑
            branchManager.switchTo(project.path(), task.getBranchName().orElseThrow(
                    () -> new DomainException("任务没有分支名，无法续跑：" + task.getId())));
            task.beginContinuation();
            return;
        }
        throw new DomainException("任务当前状态不可派发：" + task.getId() + "，状态：" + task.getStatus());
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
