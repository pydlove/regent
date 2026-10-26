package com.pydlove.regent.gitcommit.application.query;

import com.pydlove.regent.gitcommit.domain.model.GitCommitStatus;

import java.time.Instant;

/** Git 提交列表与任务摘要使用的只读投影。 */
public record GitCommitSummaryView(
        long id,
        long projectId,
        String projectName,
        long taskId,
        String taskTitle,
        String ticket,
        String message,
        String branchName,
        String commitSha,
        GitCommitStatus status,
        String failureReason,
        String operatorName,
        Instant startedAt,
        Instant finishedAt,
        long reviewSnapshotId) {
}
