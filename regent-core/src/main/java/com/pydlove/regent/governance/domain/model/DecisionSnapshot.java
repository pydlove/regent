package com.pydlove.regent.governance.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.time.Instant;
import java.util.List;

/**
 * 决策单整行快照，只用于「从存储重建领域对象」。
 *
 * <p>与 {@code TaskSnapshot} 同样的理由：把十九个字段打包，是为了让 {@code Decision.restore}
 * 的签名可读，而不是让仓储去调一串重建用的 setter —— 那等于把聚合的封装撕开一个永久的口子。
 *
 * <p>可空字段（{@code matchedRuleId}、{@code recommendation}、{@code blastRadius}、
 * {@code resolvedBy}、{@code resolvedChoice}、{@code resolutionNote}、{@code resolvedAt}）
 * 用 null 表示「数据库里就是 NULL」。
 */
public record DecisionSnapshot(
        DecisionId id,
        TaskId taskId,
        ProjectId projectId,
        DecisionLevel aiLevel,
        DecisionLevel effectiveLevel,
        Long matchedRuleId,
        DecisionStatus status,
        String question,
        List<String> options,
        String recommendation,
        boolean reversible,
        String blastRadius,
        ResolvedBy resolvedBy,
        String resolvedChoice,
        String resolutionNote,
        Instant raisedAt,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt) {
}
