package com.pydlove.regent.plan.application.command;

import com.pydlove.regent.execution.application.command.TaskDispatchService;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.plan.domain.model.Plan;
import com.pydlove.regent.plan.domain.model.PlanId;
import com.pydlove.regent.plan.domain.model.PlanRun;
import com.pydlove.regent.plan.domain.model.PlanRunItem;
import com.pydlove.regent.plan.domain.model.PlanTaskSnapshot;
import com.pydlove.regent.plan.domain.model.PlanTaskStatus;
import com.pydlove.regent.plan.domain.model.PlanTrigger;
import com.pydlove.regent.plan.domain.repository.PlanRepository;
import com.pydlove.regent.plan.domain.repository.PlanRunRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 计划执行编排。
 *
 * <p>启动时先冻结计划与任务标题，再复用 {@link TaskDispatchService} 逐项派发；
 * 刷新时只观察任务当前状态，所有任务收场后才生成本轮总结。
 */
public class PlanExecutionService {

    private final PlanRepository planRepository;
    private final PlanRunRepository planRunRepository;
    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final TaskDispatchService taskDispatchService;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public PlanExecutionService(PlanRepository planRepository,
                                PlanRunRepository planRunRepository,
                                TaskRepository taskRepository,
                                TaskRunRepository taskRunRepository,
                                TaskDispatchService taskDispatchService,
                                TransactionRunner transactionRunner,
                                Clock clock) {
        this.planRepository = planRepository;
        this.planRunRepository = planRunRepository;
        this.taskRepository = taskRepository;
        this.taskRunRepository = taskRunRepository;
        this.taskDispatchService = taskDispatchService;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public PlanRun runNow(PlanId planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new EntityNotFoundException("计划不存在：" + planId));
        return start(plan, PlanTrigger.MANUAL);
    }

    public int startDuePlans(int limit) {
        if (limit <= 0) {
            return 0;
        }
        int started = 0;
        for (Plan plan : planRepository.findDue(now(), limit)) {
            try {
                start(plan, PlanTrigger.SCHEDULED);
                started++;
            } catch (RuntimeException ignored) {
                // 单个计划失败不能阻断同一批其它计划；下一拍会再次尝试。
            }
        }
        return started;
    }

    public int refreshRunningRuns(int limit) {
        if (limit <= 0) {
            return 0;
        }
        int settled = 0;
        for (PlanRun run : planRunRepository.findRunning(limit)) {
            try {
                if (refresh(run)) {
                    settled++;
                }
            } catch (RuntimeException ignored) {
                // 任务查询等瞬时故障留给下一拍重试，已写下的执行结果不丢。
            }
        }
        return settled;
    }

    private PlanRun start(Plan plan, PlanTrigger trigger) {
        Instant now = now();
        return transactionRunner.inTransaction(() -> {
            Plan locked = planRepository.findByIdForUpdate(plan.getId())
                    .orElseThrow(() -> new EntityNotFoundException("计划不存在：" + plan.getId()));
            locked.start(now, trigger);
            planRepository.save(locked);

            List<PlanTaskSnapshot> snapshots = new ArrayList<>();
            for (TaskId taskId : locked.getTaskIds()) {
                Task task = taskRepository.findById(taskId)
                        .orElseThrow(() -> new EntityNotFoundException("计划中的任务不存在：" + taskId));
                snapshots.add(new PlanTaskSnapshot(taskId, task.getTitle()));
            }

            PlanRun run = PlanRun.start(locked.getId(), locked.getName(), trigger, snapshots);
            planRunRepository.save(run);
            for (PlanTaskSnapshot snapshot : snapshots) {
                try {
                    taskDispatchService.dispatchAfterCommit(snapshot.taskId());
                    run.recordDispatched(snapshot.taskId());
                } catch (RuntimeException exception) {
                    run.recordDispatchFailure(snapshot.taskId(), messageOf(exception));
                }
            }
            planRunRepository.save(run);
            return run;
        });
    }

    private boolean refresh(PlanRun run) {
        return transactionRunner.inTransaction(() -> {
            for (PlanRunItem item : run.getItems()) {
                if (!item.getStatus().isActive()) {
                    continue;
                }
                Task task = taskRepository.findById(item.getTaskId()).orElse(null);
                if (task == null) {
                    run.observe(item.getTaskId(), PlanTaskStatus.FAILED, "任务已不存在");
                    continue;
                }
                String detail = taskRunRepository.findLatestByTask(item.getTaskId())
                        .map(TaskRun::getSummary)
                        .orElse(null);
                run.observe(item.getTaskId(), map(task.getStatus()), detail);
            }

            boolean finished = run.settleIfFinished();
            planRunRepository.save(run);
            if (!finished) {
                return false;
            }

            Plan plan = planRepository.findById(run.getPlanId())
                    .orElseThrow(() -> new EntityNotFoundException("计划不存在：" + run.getPlanId()));
            plan.complete(now());
            planRepository.save(plan);
            return true;
        });
    }

    private static PlanTaskStatus map(TaskStatus status) {
        return switch (status) {
            case PENDING, QUEUED, RUNNING -> PlanTaskStatus.RUNNING;
            case BLOCKED -> PlanTaskStatus.BLOCKED;
            case DONE -> PlanTaskStatus.SUCCEEDED;
            case FAILED -> PlanTaskStatus.FAILED;
            case CANCELLED -> PlanTaskStatus.CANCELLED;
        };
    }

    private static String messageOf(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }
}
