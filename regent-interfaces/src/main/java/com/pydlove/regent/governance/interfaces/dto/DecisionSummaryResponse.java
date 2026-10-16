package com.pydlove.regent.governance.interfaces.dto;

import com.pydlove.regent.governance.domain.repository.DecisionView;

import java.time.Instant;
import java.util.List;

/**
 * 决策单列表行与详情共用一个形状。
 *
 * <p>{@code aiLevel} 与 {@code effectiveLevel} 同时返回：只给仲裁后的等级，前端就没法展示
 * 「规则把 L1 抬成了 L3」这件事，而那正是这个系统最需要被看见的信息。
 */
public record DecisionSummaryResponse(
        long id,
        long taskId,
        String taskTitle,
        long projectId,
        String projectName,
        String aiLevel,
        String effectiveLevel,
        boolean levelEscalated,
        String matchedRuleName,
        String status,
        String question,
        List<String> options,
        String recommendation,
        boolean reversible,
        String blastRadius,
        String resolvedBy,
        String resolvedChoice,
        String resolutionNote,
        Instant raisedAt,
        Instant resolvedAt) {

    public static DecisionSummaryResponse from(DecisionView view) {
        return new DecisionSummaryResponse(
                view.id(),
                view.taskId(),
                view.taskTitle(),
                view.projectId(),
                view.projectName(),
                view.aiLevel().name(),
                view.effectiveLevel().name(),
                view.levelEscalated(),
                view.matchedRuleName(),
                view.status(),
                view.question(),
                view.options(),
                view.recommendation(),
                view.reversible(),
                view.blastRadius(),
                view.resolvedBy(),
                view.resolvedChoice(),
                view.resolutionNote(),
                view.raisedAt(),
                view.resolvedAt());
    }
}
