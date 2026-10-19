package com.pydlove.regent.execution.interfaces.dto;

/** 当前草稿查询结果；没有草稿时明确返回 null，而不是用 404 表达正常空状态。 */
public record CurrentTaskDraftResponse(TaskDraftResponse draft) {
}
