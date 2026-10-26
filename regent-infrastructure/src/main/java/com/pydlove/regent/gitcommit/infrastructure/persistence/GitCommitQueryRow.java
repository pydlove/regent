package com.pydlove.regent.gitcommit.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** Git 提交列表和详情查询行。 */
@Data
public class GitCommitQueryRow {

    private Long id;
    private Long projectId;
    private String projectName;
    private Long taskId;
    private String taskTitle;
    private Long reviewSnapshotId;
    private Long reviewDecisionId;
    private String ticket;
    private String commitMessage;
    private String branchName;
    private String commitSha;
    private String status;
    private String operatorName;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String repositoryPath;
    private String baseRef;
    private String filePathsJson;
    private String failureReason;
    private String outputSummary;
}
