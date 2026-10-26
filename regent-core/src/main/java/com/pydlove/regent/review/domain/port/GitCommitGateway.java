package com.pydlove.regent.review.domain.port;

import java.util.List;

/** 审阅通过后的本地 Git 提交端口；所有 Git 命令实现都留在基础设施层。 */
public interface GitCommitGateway {

    Inspection inspect(String projectDirectory, List<String> reviewedPaths);

    CommitResult commit(String projectDirectory, List<String> reviewedPaths, String message);

    boolean matchesCommit(String projectDirectory,
                          String commitSha,
                          List<String> reviewedPaths);

    record Inspection(
            boolean gitRepository,
            String branchName,
            String headCommit,
            boolean indexClean,
            List<String> stagedPaths) {

        public Inspection {
            branchName = branchName == null ? "" : branchName;
            headCommit = headCommit == null ? "" : headCommit;
            stagedPaths = stagedPaths == null ? List.of() : List.copyOf(stagedPaths);
        }
    }

    record CommitResult(
            String commitSha,
            String branchName) {

        public CommitResult {
            if (commitSha == null || !commitSha.matches("[0-9a-f]{40,64}")) {
                throw new IllegalArgumentException("Git 提交 SHA 格式不合法");
            }
            branchName = branchName == null ? "" : branchName;
        }
    }
}
