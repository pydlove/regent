package com.pydlove.regent.review.domain.model;

/** 发现状态只改变处理结论，不删除原始问题描述。 */
public enum ReviewFindingStatus {
    PENDING,
    CONFIRMED,
    FALSE_POSITIVE,
    FIXED;

    public boolean isResolved() {
        return this == FALSE_POSITIVE || this == FIXED;
    }
}
