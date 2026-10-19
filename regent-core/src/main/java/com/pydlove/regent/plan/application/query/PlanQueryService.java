package com.pydlove.regent.plan.application.query;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.plan.domain.model.Plan;
import com.pydlove.regent.plan.domain.model.PlanId;
import com.pydlove.regent.plan.domain.model.PlanRun;
import com.pydlove.regent.plan.domain.model.PlanRunId;
import com.pydlove.regent.plan.domain.model.PlanTaskStatus;
import com.pydlove.regent.plan.domain.repository.PlanRepository;
import com.pydlove.regent.plan.domain.repository.PlanRunRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;

/** 计划读侧。计划数量少，直接由聚合拼装视图，避免再维护一套易漂移的 SQL 读模型。 */
public class PlanQueryService {

    private final PlanRepository planRepository;
    private final PlanRunRepository planRunRepository;
    private final TaskRepository taskRepository;

    public PlanQueryService(PlanRepository planRepository,
                            PlanRunRepository planRunRepository,
                            TaskRepository taskRepository) {
        this.planRepository = planRepository;
        this.planRunRepository = planRunRepository;
        this.taskRepository = taskRepository;
    }

    public List<PlanSummaryView> list() {
        return list(null);
    }

    public List<PlanSummaryView> list(UserId ownerId) {
        return (ownerId == null ? planRepository.findAll() : planRepository.findAll(ownerId)).stream()
                .map(this::summary)
                .toList();
    }

    public PlanDetailView get(PlanId planId) {
        return get(planId, null);
    }

    public PlanDetailView get(PlanId planId, UserId ownerId) {
        Plan plan = (ownerId == null
                ? planRepository.findById(planId)
                : planRepository.findByIdOwned(planId, ownerId))
                .orElseThrow(() -> new EntityNotFoundException("计划不存在：" + planId));
        PlanRun latest = planRunRepository.findLatestByPlan(planId).orElse(null);
        List<PlanTaskView> tasks = plan.getTaskIds().stream()
                .map(taskId -> taskView(taskId, latest))
                .toList();
        List<PlanRunView> runs = planRunRepository.listByPlan(planId).stream()
                .map(PlanRunView::from)
                .toList();
        return new PlanDetailView(summary(plan), tasks, runs);
    }

    public PlanRunDetailView getRun(PlanRunId runId) {
        return getRun(runId, null);
    }

    public PlanRunDetailView getRun(PlanRunId runId, UserId ownerId) {
        PlanRun run = planRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundException("计划执行不存在：" + runId));
        if (ownerId != null && planRepository.findByIdOwned(run.getPlanId(), ownerId).isEmpty()) {
            throw new EntityNotFoundException("计划执行不存在：" + runId);
        }
        return new PlanRunDetailView(
                PlanRunView.from(run),
                run.getItems().stream()
                        .map(item -> new PlanRunItemView(
                                item.getTaskId(),
                                item.getTitle(),
                                item.getStatus(),
                                item.getDetail(),
                                item.getUpdatedAt()))
                        .toList());
    }

    private PlanSummaryView summary(Plan plan) {
        PlanRun latest = planRunRepository.findLatestByPlan(plan.getId()).orElse(null);
        return new PlanSummaryView(
                plan.getId(),
                plan.getName(),
                plan.getStatus(),
                plan.getTaskIds().size(),
                plan.getScheduledAt(),
                plan.getStartedAt(),
                plan.getFinishedAt(),
                plan.getCreatedAt(),
                latest == null ? null : PlanRunView.from(latest));
    }

    private PlanTaskView taskView(com.pydlove.regent.execution.domain.model.TaskId taskId, PlanRun latest) {
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new PlanTaskView(taskId, "任务已不存在", null, null);
        }
        PlanTaskStatus planStatus = latest == null ? null : latest.getItems().stream()
                .filter(item -> item.getTaskId().equals(taskId))
                .map(item -> item.getStatus())
                .findFirst()
                .orElse(null);
        return new PlanTaskView(taskId, task.getTitle(), task.getStatus(), planStatus);
    }
}
