package com.pydlove.regent.plan.domain.model;

/** 计划生命周期。计划只执行一次，完成后不自动重开。 */
public enum PlanStatus {
    SCHEDULED,
    RUNNING,
    COMPLETED
}
