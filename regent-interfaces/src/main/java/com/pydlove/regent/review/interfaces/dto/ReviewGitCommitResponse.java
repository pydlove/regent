package com.pydlove.regent.review.interfaces.dto;

import com.pydlove.regent.review.application.command.ReviewGitCommitView;

import java.time.Instant;
import java.util.List;

/** 提交准备状态、文件范围与最终提交事实。 */
public record ReviewGitCommitResponse(
        boolean enabled,
        boolean canCommit,
        String reason,
        String projectName,
        String branchName,
        String headCommit,
        String ticket,
        int fileCount,
        List<String> filePaths,
        String suggestedMessage,
        String validationPattern,
        String validationHint,
        String commitSha,
        Instant committedAt) {

    public static ReviewGitCommitResponse from(ReviewGitCommitView view) {
        return new ReviewGitCommitResponse(
                view.enabled(),
                view.canCommit(),
                view.reason(),
                view.projectName(),
                view.branchName(),
                view.headCommit(),
                view.ticket(),
                view.fileCount(),
                view.filePaths(),
                view.suggestedMessage(),
                view.validationPattern(),
                view.validationHint(),
                view.commitSha(),
                view.committedAt());
    }
}
