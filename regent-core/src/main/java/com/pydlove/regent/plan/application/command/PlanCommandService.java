package com.pydlove.regent.plan.application.command;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.plan.domain.model.Plan;
import com.pydlove.regent.plan.domain.repository.PlanRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.LinkedHashSet;
import java.util.List;

/** 计划的创建用例。执行编排单独放在 {@link PlanExecutionService}。 */
public class PlanCommandService {

    private final PlanRepository planRepository;
    private final TaskRepository taskRepository;

    public PlanCommandService(PlanRepository planRepository, TaskRepository taskRepository) {
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
    }

    public Plan create(CreatePlanCommand command) {
        return create(command, null);
    }

    public Plan create(CreatePlanCommand command, UserId ownerId) {
        if (command == null) {
            throw new IllegalArgumentException("缺少计划参数");
        }
        if (command.taskIds() == null || command.taskIds().isEmpty()) {
            throw new IllegalArgumentException("至少选择一个任务");
        }
        List<TaskId> taskIds = List.copyOf(new LinkedHashSet<>(command.taskIds()));
        for (TaskId taskId : taskIds) {
            if (taskId == null) {
                throw new IllegalArgumentException("任务 ID 不能为空");
            }
            (ownerId == null
                    ? taskRepository.findById(taskId)
                    : taskRepository.findByIdOwned(taskId, ownerId))
                    .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
        }
        Plan plan = Plan.schedule(command.name(), taskIds, command.scheduledAt(), ownerId);
        planRepository.save(plan);
        return plan;
    }
}
