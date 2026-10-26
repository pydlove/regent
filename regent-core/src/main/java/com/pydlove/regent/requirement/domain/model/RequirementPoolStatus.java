package com.pydlove.regent.requirement.domain.model;

/** 需求池生命周期的独立状态，不替代任务执行状态。 */
public enum RequirementPoolStatus {
    POOL,
    IN_REVIEW,
    FORMAL,
    REJECTED
}
