package com.pydlove.regent.plan.domain.model;

/** 本次计划是由定时器拉起，还是由人点击执行。 */
public enum PlanTrigger {
    SCHEDULED,
    MANUAL
}
