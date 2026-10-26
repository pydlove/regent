package com.pydlove.regent.execution.interfaces.dto;

/** 当前在线设备请求把暂时不能执行的任务持久化为排队状态。 */
public record DeviceQueueTaskRequest(String deviceId) {
}
