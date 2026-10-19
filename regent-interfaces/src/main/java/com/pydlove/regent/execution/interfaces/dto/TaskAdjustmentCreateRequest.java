package com.pydlove.regent.execution.interfaces.dto;

/**
 * 新增执行中调整方向。
 *
 * <p>{@code sourceTaskId} 与 {@code content} 必须二选一：前者表示引用一条已有任务，
 * 后者表示临时补充。不在请求里同时接收两者，避免界面上的“二选一”在接口层又变成混用。
 */
public record TaskAdjustmentCreateRequest(Long sourceTaskId, String content) {
}
