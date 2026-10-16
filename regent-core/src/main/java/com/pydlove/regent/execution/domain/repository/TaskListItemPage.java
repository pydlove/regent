package com.pydlove.regent.execution.domain.repository;

import java.util.List;

/** 任务列表分页结果。{@code total} 是满足筛选条件的总行数，不是本页行数。 */
public record TaskListItemPage(List<TaskListItem> items, long total, int page, int size) {

    public TaskListItemPage {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return size <= 0 ? 0 : (int) ((total + size - 1) / size);
    }
}
