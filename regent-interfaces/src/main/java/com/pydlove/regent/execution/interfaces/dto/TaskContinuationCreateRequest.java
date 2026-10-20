package com.pydlove.regent.execution.interfaces.dto;

/**
 * 基于已有任务创建后续任务的请求体。
 *
 * <p>项目、Codex 会话和分支都由来源任务决定，调用方不能覆盖，
 * 否则新任务就会被悄悄地接到另一条会话上。
 */
public record TaskContinuationCreateRequest(
        String prompt,
        Integer priority) {
}
