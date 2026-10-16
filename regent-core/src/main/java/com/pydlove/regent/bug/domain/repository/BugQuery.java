package com.pydlove.regent.bug.domain.repository;

import com.pydlove.regent.bug.domain.model.BugSeverity;

/** 缺陷单查询条件。 */
public record BugQuery(BugSeverity severity, BugScope scope, String keyword, int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public BugQuery {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        scope = scope == null ? BugScope.ALL : scope;
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public static BugQuery of(String severity, String scope, String keyword, Integer page, Integer size) {
        return new BugQuery(
                severity == null || severity.isBlank() ? null : BugSeverity.parse(severity),
                BugScope.parse(scope),
                keyword,
                page == null ? 1 : page,
                size == null ? DEFAULT_SIZE : size);
    }

    public int offset() {
        return (page - 1) * size;
    }
}
