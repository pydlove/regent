package com.pydlove.regent.plan.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;

/** 计划开跑时冻结的任务标题，避免执行中改名导致总结无法还原当轮内容。 */
public record PlanTaskSnapshot(TaskId taskId, String title) {
}
