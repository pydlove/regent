package com.pydlove.regent.bug.domain.repository;

import java.util.List;

/** 缺陷单分页结果。 */
public record BugPage(List<BugView> items, long total, int page, int size) {

    public BugPage {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return size <= 0 ? 0 : (int) ((total + size - 1) / size);
    }
}
