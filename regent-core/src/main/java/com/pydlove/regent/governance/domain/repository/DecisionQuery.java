package com.pydlove.regent.governance.domain.repository;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.DecisionLevel;

/**
 * 决策单查询条件。
 *
 * <p>{@code level} 过滤的是<b>仲裁后</b>的等级：使用者关心的是「哪些决策被抬到了 L3」，
 * 而不是 AI 原本自评了什么。想看自评与仲裁的差异，走列表里的 levelEscalated 标记。
 */
public record DecisionQuery(DecisionLevel level,
                            ProjectId projectId,
                            DecisionScope scope,
                            String keyword,
                            int page,
                            int size,
                            UserId ownerId) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public DecisionQuery {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        scope = scope == null ? DecisionScope.ALL : scope;
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    public DecisionQuery(DecisionLevel level,
                         ProjectId projectId,
                         DecisionScope scope,
                         String keyword,
                         int page,
                         int size) {
        this(level, projectId, scope, keyword, page, size, null);
    }

    public static DecisionQuery of(String level,
                                   String scope,
                                   Long projectId,
                                   String keyword,
                                   Integer page,
                                   Integer size) {
        return of(level, scope, projectId, keyword, page, size, null);
    }

    public static DecisionQuery of(String level,
                                   String scope,
                                   Long projectId,
                                   String keyword,
                                   Integer page,
                                   Integer size,
                                   UserId ownerId) {
        return new DecisionQuery(
                (level == null || level.isBlank()) ? null : DecisionLevel.parse(level),
                projectId == null ? null : ProjectId.of(projectId),
                DecisionScope.parse(scope),
                keyword,
                page == null ? 1 : page,
                size == null ? DEFAULT_SIZE : size,
                ownerId);
    }

    public static DecisionQuery firstPage() {
        return new DecisionQuery(null, null, DecisionScope.ALL, null, 1, DEFAULT_SIZE, null);
    }

    public int offset() {
        return (page - 1) * size;
    }
}
