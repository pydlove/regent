package com.pydlove.regent.gitcommit.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code git_commit_record} 数据对象。 */
@Data
public class GitCommitRecordData {

    private Long id;
    private Long projectId;
    private Long taskId;
    private Long taskRunId;
    private Long reviewSnapshotId;
    private Long reviewDecisionId;
    private Long operatorId;
    private String projectName;
    private String taskTitle;
    private String repositoryPath;
    private String ticket;
    private String commitMessage;
    private String branchName;
    private String baseRef;
    private String commitSha;
    private String filePathsJson;
    private String status;
    private String failureReason;
    private String outputSummary;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
