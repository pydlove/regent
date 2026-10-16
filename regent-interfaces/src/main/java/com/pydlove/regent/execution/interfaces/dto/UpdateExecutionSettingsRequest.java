package com.pydlove.regent.execution.interfaces.dto;

/** 更新平台执行设置。最大并发数由领域对象校验，接口层不复制一套范围常量。 */
public record UpdateExecutionSettingsRequest(int maxConcurrentTasks) {
}
