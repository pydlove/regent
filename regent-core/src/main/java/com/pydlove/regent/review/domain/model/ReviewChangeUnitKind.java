package com.pydlove.regent.review.domain.model;

/** 变更单元的审阅类别。 */
public enum ReviewChangeUnitKind {
    BUSINESS,
    GENERATED,
    LOCKFILE,
    FORMATTING,
    BINARY,
    UNKNOWN;

    public boolean isPrimaryReviewUnit() {
        return this == BUSINESS || this == UNKNOWN;
    }
}
