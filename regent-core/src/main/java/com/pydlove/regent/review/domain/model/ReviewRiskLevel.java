package com.pydlove.regent.review.domain.model;

/** 变更风险等级只允许向上合并，避免规则把更保守的判断降级。 */
public enum ReviewRiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    public ReviewRiskLevel higherOf(ReviewRiskLevel other) {
        if (other == null || ordinal() >= other.ordinal()) {
            return this;
        }
        return other;
    }
}
