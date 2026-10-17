package com.pydlove.regent.plan.domain.model;

/** 计划单次执行的最终或进行中状态。 */
public enum PlanRunStatus {
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED
}
