package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 需求 ID。 */
public record RequirementId(long value) {

    public RequirementId {
        if (value <= 0) {
            throw new DomainException("需求 ID 必须为正数：" + value);
        }
    }

    public static RequirementId of(long value) {
        return new RequirementId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
