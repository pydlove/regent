package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.TaskId;

import java.util.List;

/**
 * 批量删除结果。
 *
 * <p>允许部分成功：某个任务在确认期间已被派发或状态已经变化时，只报告该项失败，
 * 不撤销同一批里已经删除成功的任务。
 */
public record TaskDeleteBatchResult(
        List<TaskId> deletedTaskIds,
        List<Failure> failures) {

    public TaskDeleteBatchResult {
        deletedTaskIds = List.copyOf(deletedTaskIds);
        failures = List.copyOf(failures);
    }

    public record Failure(TaskId taskId, String message) {
    }
}
