package com.pydlove.regent.gitcommit.application.query;

import com.pydlove.regent.gitcommit.domain.model.GitCommitStatus;

import java.time.Instant;

/** Git 提交列表筛选条件。 */
public record GitCommitFilter(
        Long projectId,
        Long taskId,
        GitCommitStatus status,
        String keyword,
        Instant from,
        Instant to,
        int page,
        int size) {

    public GitCommitFilter {
        keyword = keyword == null ? "" : keyword.strip();
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 20;
        }
    }
}
