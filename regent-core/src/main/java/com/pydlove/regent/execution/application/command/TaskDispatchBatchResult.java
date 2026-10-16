package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.TaskId;

import java.util.List;

/**
 * 批量派发结果。
 *
 * <p>批量操作允许部分成功：某个项目暂停、某条任务已被别的入口认领，只影响对应任务，
 * 不应把同一批里本来可以正常起跑或进入排队的任务一起回滚。
 */
public record TaskDispatchBatchResult(
        List<TaskId> dispatchedTaskIds,
        List<Failure> failures) {

    public TaskDispatchBatchResult {
        dispatchedTaskIds = List.copyOf(dispatchedTaskIds);
        failures = List.copyOf(failures);
    }

    public record Failure(TaskId taskId, String message) {
    }
}
