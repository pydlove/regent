package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.command.TaskDispatchBatchResult;

import java.util.List;

/** 批量派发结果。成功包括立即起跑与进入排队，失败项单独返回供用户重试。 */
public record TaskBatchDispatchResponse(
        List<Long> dispatchedTaskIds,
        List<TaskBatchDispatchFailureResponse> failures) {

    public static TaskBatchDispatchResponse from(TaskDispatchBatchResult result) {
        return new TaskBatchDispatchResponse(
                result.dispatchedTaskIds().stream().map(taskId -> taskId.value()).toList(),
                result.failures().stream()
                        .map(failure -> new TaskBatchDispatchFailureResponse(
                                failure.taskId().value(), failure.message()))
                        .toList());
    }
}
