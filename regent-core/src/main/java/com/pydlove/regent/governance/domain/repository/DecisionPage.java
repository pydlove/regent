package com.pydlove.regent.governance.domain.repository;

import java.util.List;

/** 决策单分页结果，语义与 {@code TaskPage} 一致：{@code total} 是满足条件的总行数。 */
public record DecisionPage(List<DecisionView> items, long total, int page, int size) {

    public DecisionPage {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return size <= 0 ? 0 : (int) ((total + size - 1) / size);
    }
}
