package com.pydlove.regent.plan.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 计划 ID。 */
public record PlanId(long value) {

    public PlanId {
        if (value <= 0) {
            throw new DomainException("计划 ID 必须为正数：" + value);
        }
    }

    public static PlanId of(long value) {
        return new PlanId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
