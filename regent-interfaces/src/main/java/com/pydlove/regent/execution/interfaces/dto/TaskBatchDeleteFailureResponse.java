package com.pydlove.regent.execution.interfaces.dto;

/** 批量删除中的一个失败项。 */
public record TaskBatchDeleteFailureResponse(long taskId, String message) {
}
