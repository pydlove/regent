package com.pydlove.regent.bug.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 缺陷单 ID。 */
public record BugId(long value) {

    public BugId {
        if (value <= 0) {
            throw new DomainException("缺陷单 ID 必须为正数：" + value);
        }
    }

    public static BugId of(long value) {
        return new BugId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
