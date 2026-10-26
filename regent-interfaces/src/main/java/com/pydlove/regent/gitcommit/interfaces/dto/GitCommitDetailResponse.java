package com.pydlove.regent.gitcommit.interfaces.dto;

import com.pydlove.regent.gitcommit.application.query.GitCommitDetailView;

import java.util.List;

public record GitCommitDetailResponse(
        GitCommitSummaryResponse summary,
        long reviewDecisionId,
        String repositoryPath,
        String baseRef,
        List<String> filePaths,
        String failureReason,
        String outputSummary) {

    public static GitCommitDetailResponse from(GitCommitDetailView view) {
        return new GitCommitDetailResponse(
                GitCommitSummaryResponse.from(view.summary()),
                view.reviewDecisionId(),
                view.repositoryPath(),
                view.baseRef(),
                view.filePaths(),
                view.failureReason(),
                view.outputSummary());
    }
}
