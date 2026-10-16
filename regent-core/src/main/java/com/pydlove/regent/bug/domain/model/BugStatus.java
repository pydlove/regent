package com.pydlove.regent.bug.domain.model;

/** 缺陷处理状态。 */
public enum BugStatus {
    OPEN,
    FIXING,
    FIXED,
    CLOSED,
    WONTFIX;

    public boolean isResolved() {
        return this == CLOSED || this == WONTFIX;
    }
}
