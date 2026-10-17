package com.pydlove.regent.plan.domain.model;

/** 计划内某条任务在本轮执行中的状态。 */
public enum PlanTaskStatus {
    PENDING,
    RUNNING,
    BLOCKED,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    SKIPPED;

    public boolean isActive() {
        return this == PENDING || this == RUNNING || this == BLOCKED;
    }
}
