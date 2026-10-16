package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.command.TaskDeleteBatchResult;

import java.util.List;

/** 批量删除结果。成功删除与未能删除的任务分开返回。 */
public record TaskBatchDeleteResponse(
        List<Long> deletedTaskIds,
        List<TaskBatchDeleteFailureResponse> failures) {

    public static TaskBatchDeleteResponse from(TaskDeleteBatchResult result) {
        return new TaskBatchDeleteResponse(
                result.deletedTaskIds().stream().map(taskId -> taskId.value()).toList(),
                result.failures().stream()
                        .map(failure -> new TaskBatchDeleteFailureResponse(
                                failure.taskId().value(), failure.message()))
                        .toList());
    }
}
