package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.repository.TaskListItem;

import java.time.Instant;

/** 任务列表行。字段与前端 {@code types/task.ts} 的展示需求对齐，不夹带领域对象。 */
public record TaskSummaryResponse(
        long id,
        long projectId,
        String projectName,
        String source,
        String title,
        String status,
        int priority,
        String level,
        String branchName,
        Long decisionId,
        int attemptCount,
        long tokens,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskSummaryResponse from(TaskListItem item) {
        return new TaskSummaryResponse(
                item.id(),
                item.projectId(),
                item.projectName(),
                item.source().name(),
                item.title(),
                item.status().name(),
                item.priority(),
                item.level().name(),
                item.branchName(),
                item.blockingDecisionId() == null ? null : item.blockingDecisionId().value(),
                item.attemptCount(),
                item.tokens(),
                item.startedAt(),
                item.finishedAt(),
                item.createdAt(),
                item.updatedAt());
    }
}
