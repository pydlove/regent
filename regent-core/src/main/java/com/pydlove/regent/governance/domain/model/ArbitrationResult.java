package com.pydlove.regent.governance.domain.model;

import com.pydlove.regent.shared.kernel.DecisionLevel;

/**
 * 仲裁结果。
 *
 * <p>{@code aiLevel} 与 {@code effectiveLevel} 都要落库（{@code decision.ai_level} 与
 * {@code decision.effective_level}）。两者不一致本身就说明分级规则起了作用，这是事后审计
 * 「这条决策到底是 AI 拍的还是你批的、依据什么」的关键证据。
 *
 * @param matchedRuleId   命中的最高优先级规则；null 表示仅凭 AI 自评
 * @param matchedRuleName 规则名，便于直接看日志定位
 * @param escalated       规则是否真的抬高了等级
 */
public record ArbitrationResult(
        DecisionLevel aiLevel,
        DecisionLevel effectiveLevel,
        DecisionLevel autoThreshold,
        Long matchedRuleId,
        String matchedRuleName,
        boolean escalated) {

    /** 生效等级高于阈值时，必须由人来定。 */
    public boolean requiresHumanApproval() {
        return !effectiveLevel.isWithin(autoThreshold);
    }

    /** 生效等级落在阈值内，可以自动决断并留痕。 */
    public boolean autoResolvable() {
        return !requiresHumanApproval();
    }
}
