package com.pydlove.regent.bug.domain.repository;

import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;

/** 缺陷单查询条件。 */
public record BugQuery(BugSeverity severity,
                       ProjectId projectId,
                       BugScope scope,
                       String keyword,
                       int page,
                       int size,
                       UserId ownerId) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public BugQuery {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        scope = scope == null ? BugScope.ALL : scope;
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public BugQuery(BugSeverity severity,
                    ProjectId projectId,
                    BugScope scope,
                    String keyword,
                    int page,
                    int size) {
        this(severity, projectId, scope, keyword, page, size, null);
    }

    public static BugQuery of(String severity,
                              String scope,
                              Long projectId,
                              String keyword,
                              Integer page,
                              Integer size) {
        return of(severity, scope, projectId, keyword, page, size, null);
    }

    public static BugQuery of(String severity,
                              String scope,
                              Long projectId,
                              String keyword,
                              Integer page,
                              Integer size,
                              UserId ownerId) {
        return new BugQuery(
                severity == null || severity.isBlank() ? null : BugSeverity.parse(severity),
                projectId == null ? null : ProjectId.of(projectId),
                BugScope.parse(scope),
                keyword,
                page == null ? 1 : page,
                size == null ? DEFAULT_SIZE : size,
                ownerId);
    }

    public int offset() {
        return (page - 1) * size;
    }
}
