package com.pydlove.regent.plan.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 计划单次执行记录 ID。 */
public record PlanRunId(long value) {

    public PlanRunId {
        if (value <= 0) {
            throw new DomainException("计划执行 ID 必须为正数：" + value);
        }
    }

    public static PlanRunId of(long value) {
        return new PlanRunId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
