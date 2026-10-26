package com.pydlove.regent.gitcommit.interfaces.dto;

import com.pydlove.regent.gitcommit.application.query.GitCommitSummaryView;

import java.time.Instant;

public record GitCommitSummaryResponse(
        long id,
        long projectId,
        String projectName,
        long taskId,
        String taskTitle,
        String ticket,
        String message,
        String branchName,
        String commitSha,
        String status,
        String failureReason,
        String operatorName,
        Instant startedAt,
        Instant finishedAt,
        long reviewSnapshotId) {

    public static GitCommitSummaryResponse from(GitCommitSummaryView view) {
        return new GitCommitSummaryResponse(
                view.id(),
                view.projectId(),
                view.projectName(),
                view.taskId(),
                view.taskTitle(),
                view.ticket(),
                view.message(),
                view.branchName(),
                view.commitSha(),
                view.status().name(),
                view.failureReason(),
                view.operatorName(),
                view.startedAt(),
                view.finishedAt(),
                view.reviewSnapshotId());
    }
}
