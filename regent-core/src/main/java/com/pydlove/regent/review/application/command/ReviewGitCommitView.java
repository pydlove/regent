package com.pydlove.regent.review.application.command;

import java.time.Instant;
import java.util.List;

/** 审阅通过后的 Git 提交准备状态；界面据此展示生成说明、文件范围和阻断原因。 */
public record ReviewGitCommitView(
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

    public ReviewGitCommitView {
        reason = reason == null ? "" : reason;
        projectName = projectName == null ? "" : projectName;
        branchName = branchName == null ? "" : branchName;
        headCommit = headCommit == null ? "" : headCommit;
        ticket = ticket == null ? "" : ticket;
        filePaths = filePaths == null ? List.of() : List.copyOf(filePaths);
        suggestedMessage = suggestedMessage == null ? "" : suggestedMessage;
        validationPattern = validationPattern == null ? "" : validationPattern;
        validationHint = validationHint == null ? "" : validationHint;
        commitSha = commitSha == null ? "" : commitSha;
    }
}
