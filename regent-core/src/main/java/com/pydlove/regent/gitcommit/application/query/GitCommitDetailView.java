package com.pydlove.regent.gitcommit.application.query;

import java.util.List;

/** Git 提交详情。 */
public record GitCommitDetailView(
        GitCommitSummaryView summary,
        long reviewDecisionId,
        String repositoryPath,
        String baseRef,
        List<String> filePaths,
        String failureReason,
        String outputSummary) {

    public GitCommitDetailView {
        filePaths = filePaths == null ? List.of() : List.copyOf(filePaths);
    }
}
