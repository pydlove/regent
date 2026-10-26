package com.pydlove.regent.gitcommit.application.query;

import java.util.List;

/** Git 提交分页结果。 */
public record GitCommitPageView(
        List<GitCommitSummaryView> items,
        long total,
        int page,
        int size) {

    public GitCommitPageView {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
