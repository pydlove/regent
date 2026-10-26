package com.pydlove.regent.gitcommit.application.command;

import com.pydlove.regent.gitcommit.domain.model.GitCommitDecisionId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitOperatorId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitProjectId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitSnapshotId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitTaskId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitTaskRunId;

import java.util.List;

/** 记录一次即将执行的 Git 提交尝试。 */
public record BeginGitCommitCommand(
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
        List<String> filePaths) {
}
