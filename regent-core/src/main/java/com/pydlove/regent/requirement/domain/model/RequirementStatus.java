package com.pydlove.regent.requirement.domain.model;

/** 需求生命周期。执行中的细分状态由关联任务状态推导，不直接写回数据库。 */
public enum RequirementStatus {
    DRAFT,
    CLARIFYING,
    READY,
    PLANNED,
    RUNNING,
    PARTIAL,
    DONE,
    CANCELLED
}
