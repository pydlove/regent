package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 审阅快照的强类型 ID。 */
public record ReviewSnapshotId(long value) {

    public ReviewSnapshotId {
        if (value < 1) {
            throw new DomainException("审阅快照 ID 必须为正数：" + value);
        }
    }

    public static ReviewSnapshotId of(long value) {
        return new ReviewSnapshotId(value);
    }
}
