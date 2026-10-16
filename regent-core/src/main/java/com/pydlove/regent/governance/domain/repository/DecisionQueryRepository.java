package com.pydlove.regent.governance.domain.repository;

import java.util.Optional;

/**
 * 决策单查询仓储（只读）。
 *
 * <p>命名里带 Query 是刻意的：它只提供读模型，不与将来的 Decision 聚合仓储混在一个接口里。
 * 一个接口若同时承担「取聚合来改」和「取视图来渲染」，聚合的字段需求很快会被读模型带偏。
 */
public interface DecisionQueryRepository {

    DecisionPage findPage(DecisionQuery query);

    Optional<DecisionView> findById(long id);
}
