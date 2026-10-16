package com.pydlove.regent.governance.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/**
 * 决策单状态。与 {@code decision.status} 列逐字对齐。
 *
 * <p>{@code AUTO_RESOLVED} 与 {@code APPROVED} 分开：前者是 AI 按分级规则自行决断并留痕，
 * 后者是人工批准。二者的审计含义完全不同，合成一个值就再也分不出「谁做的决定」。
 */
public enum DecisionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    AUTO_RESOLVED;

    public static DecisionStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("决策状态不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("无法识别的决策状态：" + raw);
        }
    }
}
