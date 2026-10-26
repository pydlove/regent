package com.pydlove.regent.review.domain.model;

/** 单个 diff 审阅项的当前人工决策。 */
public enum ReviewUnitDecisionStatus {
    PENDING,
    APPROVED,
    CHANGES_REQUESTED,
    REJECTED
}
