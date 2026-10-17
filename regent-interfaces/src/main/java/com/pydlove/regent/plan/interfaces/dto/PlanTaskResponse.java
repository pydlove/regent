package com.pydlove.regent.plan.interfaces.dto;

import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.plan.application.query.PlanTaskView;
import com.pydlove.regent.plan.domain.model.PlanTaskStatus;

/** 计划中的任务响应。 */
public record PlanTaskResponse(
        long id,
        String title,
        TaskStatus taskStatus,
        PlanTaskStatus planTaskStatus) {

    public static PlanTaskResponse from(PlanTaskView view) {
        return new PlanTaskResponse(
                view.taskId().value(),
                view.title(),
                view.taskStatus(),
                view.planTaskStatus());
    }
}
