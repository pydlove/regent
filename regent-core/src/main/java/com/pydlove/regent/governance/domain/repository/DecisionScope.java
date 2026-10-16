package com.pydlove.regent.governance.domain.repository;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/**
 * 决策列表的「待批 / 已决」筛选。
 *
 * <p>不是把 {@code DecisionStatus} 直接当条件用：使用者看的是「还有几件事等我拍板」，
 * 这是一个关于列表的问题；已决那一侧要同时包含批准、驳回、AI 自动决断三种状态。
 */
public enum DecisionScope {
    ALL,
    PENDING,
    RESOLVED;

    public static DecisionScope parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("无法识别的决策范围：" + raw);
        }
    }
}
