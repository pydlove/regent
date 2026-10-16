package com.pydlove.regent.governance.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/**
 * 谁做的决定 —— 决策留痕里最不能省的一列。
 *
 * <p>它和 {@link DecisionStatus} 是两个正交的维度：状态说的是「结论是什么」，本枚举说的是
 * 「谁拍的板」。压成一个字段（比如 {@code AI_APPROVED} / {@code HUMAN_APPROVED}）会立刻
 * 丢掉一个维度：将来若要按「AI 自主决断占比」统计，或者审计「某个高风险改动是不是 AI 自己
 * 放行的」，就再也算不出来了。
 */
public enum ResolvedBy {

    /** 执行器按分级规则自行决断，只留痕、不打扰人。 */
    AI,

    /** 人工批准或驳回。 */
    HUMAN;

    public static ResolvedBy parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("决议人不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("无法识别的决议人：" + raw);
        }
    }
}
