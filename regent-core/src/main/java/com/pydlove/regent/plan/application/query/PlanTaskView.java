package com.pydlove.regent.plan.application.query;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.plan.domain.model.PlanTaskStatus;

/** 计划详情中的一条任务及其本轮状态。 */
public record PlanTaskView(
        TaskId taskId,
        String title,
        TaskStatus taskStatus,
        PlanTaskStatus planTaskStatus) {
}
