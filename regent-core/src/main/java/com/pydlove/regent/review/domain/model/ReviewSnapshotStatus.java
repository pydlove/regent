package com.pydlove.regent.review.domain.model;

/** 审阅状态与任务状态彼此独立。 */
public enum ReviewSnapshotStatus {
    WAITING_REVIEW,
    IN_REVIEW,
    CHANGES_REQUESTED,
    APPROVED,
    COMMITTED,
    REJECTED,
    EXPIRED;

    public boolean isOpen() {
        return this == WAITING_REVIEW || this == IN_REVIEW;
    }
}
