package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.query.AgentTaskSummary;

import java.time.Instant;

/** 可领任务清单里的一行。字段名即协议：执行者按它们写代码。 */
public record AgentTaskSummaryResponse(long taskId,
                                       String projectCode,
                                       String title,
                                       String status,
                                       int priority,
                                       String source,
                                       Instant createdAt,
                                       boolean claimable,
                                       String reason) {

    public static AgentTaskSummaryResponse from(AgentTaskSummary summary) {
        return new AgentTaskSummaryResponse(summary.taskId(), summary.projectCode(), summary.title(),
                summary.status(), summary.priority(), summary.source(), summary.createdAt(),
                summary.claimable(), summary.reason());
    }
}
