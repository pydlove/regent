package com.pydlove.regent.plan.application.command;

import com.pydlove.regent.execution.domain.model.TaskId;

import java.time.Instant;
import java.util.List;

/** 创建计划。任务集合在创建时固定，执行时间由定时器扫描。 */
public record CreatePlanCommand(
        String name,
        List<TaskId> taskIds,
        Instant scheduledAt) {
}
