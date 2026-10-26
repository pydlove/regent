package com.pydlove.regent.gitcommit.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 平台发起的一次 Git 提交尝试。
 *
 * <p>历史记录只追加。失败记录不会被改写成成功记录，重试会创建新的聚合。
 */
public class GitCommitRecord {

    public static final int MAX_MESSAGE_LENGTH = 4000;
    public static final int MAX_OUTPUT_LENGTH = 4000;
    public static final int MAX_FAILURE_LENGTH = 1024;

    private GitCommitRecordId id;
    private final GitCommitProjectId projectId;
    private final GitCommitTaskId taskId;
    private final GitCommitTaskRunId taskRunId;
    private final GitCommitSnapshotId snapshotId;
    private final GitCommitDecisionId decisionId;
    private final GitCommitOperatorId operatorId;
    private final String projectName;
    private final String taskTitle;
    private final String repositoryPath;
    private final String ticket;
    private final String message;
    private final String branchName;
    private final String baseRef;
    private final List<String> filePaths;
    private GitCommitStatus status;
    private String commitSha;
    private String failureReason;
    private String outputSummary;
    private final Instant startedAt;
    private Instant finishedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private GitCommitRecord(GitCommitRecordId id,
                            GitCommitProjectId projectId,
                            GitCommitTaskId taskId,
                            GitCommitTaskRunId taskRunId,
                            GitCommitSnapshotId snapshotId,
                            GitCommitDecisionId decisionId,
                            GitCommitOperatorId operatorId,
                            String projectName,
                            String taskTitle,
                            String repositoryPath,
                            String ticket,
                            String message,
                            String branchName,
                            String baseRef,
                            List<String> filePaths,
                            GitCommitStatus status,
                            String commitSha,
                            String failureReason,
                            String outputSummary,
                            Instant startedAt,
                            Instant finishedAt,
                            Instant createdAt,
                            Instant updatedAt) {
        this.id = id;
        this.projectId = requireNotNull(projectId, "Git 提交项目");
        this.taskId = requireNotNull(taskId, "Git 提交任务");
        this.taskRunId = requireNotNull(taskRunId, "Git 提交执行记录");
        this.snapshotId = requireNotNull(snapshotId, "Git 提交审阅快照");
        this.decisionId = requireNotNull(decisionId, "Git 提交审阅结论");
        this.operatorId = requireNotNull(operatorId, "Git 提交操作者");
        this.projectName = blankToNull(projectName);
        this.taskTitle = blankToNull(taskTitle);
        this.repositoryPath = requireText(repositoryPath, "Git 仓库路径");
        this.ticket = blankToNull(ticket);
        if (this.ticket != null && !this.ticket.matches("\\d{6}")) {
            throw new DomainException("Git 提交 Bug 编号必须是 6 位数字");
        }
        this.message = requireText(message, "Git 提交说明");
        if (this.message.length() > MAX_MESSAGE_LENGTH) {
            throw new DomainException("Git 提交说明不能超过 " + MAX_MESSAGE_LENGTH + " 个字符");
        }
        this.branchName = requireText(branchName, "Git 提交分支");
        this.baseRef = blankToNull(baseRef);
        this.filePaths = filePaths == null ? List.of() : filePaths.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
        if (this.filePaths.isEmpty()) {
            throw new DomainException("Git 提交至少需要一个文件");
        }
        this.status = requireNotNull(status, "Git 提交状态");
        this.commitSha = blankToNull(commitSha);
        this.failureReason = blankToNull(failureReason);
        this.outputSummary = truncate(blankToNull(outputSummary));
        this.startedAt = requireNotNull(startedAt, "Git 提交开始时间");
        this.finishedAt = finishedAt;
        this.createdAt = requireNotNull(createdAt, "Git 提交创建时间");
        this.updatedAt = requireNotNull(updatedAt, "Git 提交更新时间");
        validateState();
    }

    public static GitCommitRecord start(GitCommitProjectId projectId,
                                        GitCommitTaskId taskId,
                                        GitCommitTaskRunId taskRunId,
                                        GitCommitSnapshotId snapshotId,
                                        GitCommitDecisionId decisionId,
                                        GitCommitOperatorId operatorId,
                                        String projectName,
                                        String taskTitle,
                                        String repositoryPath,
                                        String ticket,
                                        String message,
                                        String branchName,
                                        String baseRef,
                                        List<String> filePaths,
                                        Instant now) {
        String normalizedTicket = blankToNull(ticket);
        if (normalizedTicket != null && !normalizedTicket.matches("\\d{6}")) {
            throw new DomainException("Git 提交 Bug 编号必须是 6 位数字");
        }
        Instant at = requireNotNull(now, "Git 提交开始时间").truncatedTo(ChronoUnit.MILLIS);
        return new GitCommitRecord(null, projectId, taskId, taskRunId, snapshotId, decisionId,
                operatorId, projectName, taskTitle, repositoryPath, normalizedTicket, message,
                branchName, baseRef, filePaths, GitCommitStatus.RUNNING, null, null, null,
                at, null, at, at);
    }

    public static GitCommitRecord restore(GitCommitRecordId id,
                                          GitCommitProjectId projectId,
                                          GitCommitTaskId taskId,
                                          GitCommitTaskRunId taskRunId,
                                          GitCommitSnapshotId snapshotId,
                                          GitCommitDecisionId decisionId,
                                          GitCommitOperatorId operatorId,
                                          String projectName,
                                          String taskTitle,
                                          String repositoryPath,
                                          String ticket,
                                          String message,
                                          String branchName,
                                          String baseRef,
                                          List<String> filePaths,
                                          GitCommitStatus status,
                                          String commitSha,
                                          String failureReason,
                                          String outputSummary,
                                          Instant startedAt,
                                          Instant finishedAt,
                                          Instant createdAt,
                                          Instant updatedAt) {
        return new GitCommitRecord(requireNotNull(id, "Git 提交记录 ID"), projectId, taskId,
                taskRunId, snapshotId, decisionId, operatorId, projectName, taskTitle,
                repositoryPath, ticket, message, branchName, baseRef, filePaths, status,
                commitSha, failureReason, outputSummary, startedAt, finishedAt, createdAt, updatedAt);
    }

    public void succeed(String sha, String branchName, String output, Instant now) {
        requireRunning();
        String normalizedSha = requireText(sha, "Git 提交 SHA");
        if (!normalizedSha.matches("[0-9a-f]{40,64}")) {
            throw new DomainException("Git 提交 SHA 格式不合法");
        }
        this.commitSha = normalizedSha;
        if (branchName != null && !branchName.isBlank()) {
            // branchName is final projection captured before commit; Git reports the same branch.
        }
        this.outputSummary = truncate(blankToNull(output));
        this.failureReason = null;
        this.status = GitCommitStatus.SUCCEEDED;
        this.finishedAt = requireNotNull(now, "Git 提交完成时间");
        this.updatedAt = finishedAt;
    }

    public void fail(String reason, String output, Instant now) {
        requireRunning();
        this.failureReason = requireText(reason, "Git 提交失败原因");
        if (failureReason.length() > MAX_FAILURE_LENGTH) {
            failureReason = failureReason.substring(0, MAX_FAILURE_LENGTH);
        }
        this.outputSummary = truncate(blankToNull(output));
        this.status = GitCommitStatus.FAILED;
        this.finishedAt = requireNotNull(now, "Git 提交失败时间");
        this.updatedAt = finishedAt;
    }

    public void assignIdentity(GitCommitRecordId assigned) {
        if (id != null) {
            throw new DomainException("Git 提交记录已有 ID，不能重复分配：" + id);
        }
        id = requireNotNull(assigned, "Git 提交记录 ID");
    }

    private void requireRunning() {
        if (status != GitCommitStatus.RUNNING) {
            throw new DomainException("只有执行中的 Git 提交可以收敛，当前状态：" + status);
        }
    }

    private void validateState() {
        if (status == GitCommitStatus.RUNNING) {
            if (commitSha != null || failureReason != null || finishedAt != null) {
                throw new DomainException("执行中的 Git 提交不能带结果字段");
            }
        } else if (status == GitCommitStatus.SUCCEEDED) {
            if (commitSha == null || finishedAt == null || failureReason != null) {
                throw new DomainException("成功的 Git 提交必须带 SHA 和完成时间");
            }
        } else if (status == GitCommitStatus.FAILED) {
            if (failureReason == null || finishedAt == null || commitSha != null) {
                throw new DomainException("失败的 Git 提交必须带失败原因，且不能带 SHA");
            }
        }
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_OUTPUT_LENGTH
                ? value
                : value.substring(0, MAX_OUTPUT_LENGTH);
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public GitCommitRecordId getId() {
        return id;
    }

    public GitCommitProjectId getProjectId() {
        return projectId;
    }

    public GitCommitTaskId getTaskId() {
        return taskId;
    }

    public GitCommitTaskRunId getTaskRunId() {
        return taskRunId;
    }

    public GitCommitSnapshotId getSnapshotId() {
        return snapshotId;
    }

    public GitCommitDecisionId getDecisionId() {
        return decisionId;
    }

    public GitCommitOperatorId getOperatorId() {
        return operatorId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public String getTicket() {
        return ticket;
    }

    public String getMessage() {
        return message;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getBaseRef() {
        return baseRef;
    }

    public List<String> getFilePaths() {
        return filePaths;
    }

    public GitCommitStatus getStatus() {
        return status;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
