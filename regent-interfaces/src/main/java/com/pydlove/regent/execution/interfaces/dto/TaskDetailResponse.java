package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.Task;

import java.time.Instant;

/** 任务详情。比列表多出 {@code prompt} 与 {@code threadId} —— 详情页要看「到底让 AI 干了什么」。 */
public record TaskDetailResponse(
        long id,
        long projectId,
        Long requirementId,
        String source,
        String title,
        String prompt,
        String status,
        int priority,
        String threadId,
        Long continuedFromTaskId,
        String branchName,
        Long decisionId,
        int attemptCount,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskDetailResponse from(Task task) {
        return new TaskDetailResponse(
                task.getId().value(),
                task.getProjectId().value(),
                task.getRequirementId().map(requirementId -> requirementId.value()).orElse(null),
                task.getSource().name(),
                task.getTitle(),
                task.getPrompt(),
                task.getStatus().name(),
                task.getPriority(),
                task.getCodexThreadId().orElse(null),
                task.getContinuedFromTaskId().map(taskId -> taskId.value()).orElse(null),
                task.getBranchName().orElse(null),
                task.getBlockingDecisionId().map(decisionId -> decisionId.value()).orElse(null),
                task.getAttemptCount(),
                task.getStartedAt().orElse(null),
                task.getFinishedAt().orElse(null),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
