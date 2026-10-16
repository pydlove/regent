package com.pydlove.regent.governance.domain.repository;

import com.pydlove.regent.governance.domain.model.DecisionStatus;
import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.time.Instant;
import java.util.List;

/**
 * 决策单读模型，列表与详情共用。
 *
 * <p>刻意不是聚合：读模型承载「展示需要什么」，聚合承载「能做什么」。把展示字段塞进聚合，
 * 会让不该出现在对外契约里的内部状态混进去；反过来，为了展示去 new 一个没有行为的
 * Decision 聚合，那才是真正的贫血模型。
 *
 * <p>决策的写侧（批准 / 驳回 / 令牌核销）尚未实现，届时才会引入带状态守卫的 Decision 聚合。
 */
public record DecisionView(
        long id,
        long taskId,
        String taskTitle,
        long projectId,
        String projectName,
        DecisionLevel aiLevel,
        DecisionLevel effectiveLevel,
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

    /** 仲裁是否把等级抬高了。这是「规则确实在起作用」的唯一证据，列表和详情都要显示。 */
    public boolean levelEscalated() {
        return aiLevel != effectiveLevel;
    }

    public boolean isPending() {
        return DecisionStatus.PENDING.name().equals(status);
    }
}
