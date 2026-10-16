package com.pydlove.regent.execution.interfaces.dto;

/** 批量派发中的一个失败项。 */
public record TaskBatchDispatchFailureResponse(long taskId, String message) {
}
