package com.pydlove.regent.governance.domain.repository;

import com.pydlove.regent.shared.kernel.DecisionLevel;

/**
 * 决策单查询条件。
 *
 * <p>{@code level} 过滤的是<b>仲裁后</b>的等级：使用者关心的是「哪些决策被抬到了 L3」，
 * 而不是 AI 原本自评了什么。想看自评与仲裁的差异，走列表里的 levelEscalated 标记。
 */
public record DecisionQuery(DecisionLevel level, DecisionScope scope, String keyword, int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public DecisionQuery {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        scope = scope == null ? DecisionScope.ALL : scope;
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    public static DecisionQuery of(String level, String scope, String keyword, Integer page, Integer size) {
        return new DecisionQuery(
                (level == null || level.isBlank()) ? null : DecisionLevel.parse(level),
                DecisionScope.parse(scope),
                keyword,
                page == null ? 1 : page,
                size == null ? DEFAULT_SIZE : size);
    }

    public static DecisionQuery firstPage() {
        return new DecisionQuery(null, DecisionScope.ALL, null, 1, DEFAULT_SIZE);
    }

    public int offset() {
        return (page - 1) * size;
    }
}
