package com.pydlove.regent.gitcommit.interfaces.dto;

import com.pydlove.regent.gitcommit.application.query.GitCommitPageView;

import java.util.List;

public record GitCommitPageResponse(
        List<GitCommitSummaryResponse> items,
        long total,
        int page,
        int size) {

    public static GitCommitPageResponse from(GitCommitPageView view) {
        return new GitCommitPageResponse(
                view.items().stream().map(GitCommitSummaryResponse::from).toList(),
                view.total(),
                view.page(),
                view.size());
    }
}
