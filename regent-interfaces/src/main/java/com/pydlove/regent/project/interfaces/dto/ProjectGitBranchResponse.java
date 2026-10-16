package com.pydlove.regent.project.interfaces.dto;

/**
 * 项目当前 Git 分支。没有 Git 管理或处于游离 HEAD 时为 {@code null}。
 */
public record ProjectGitBranchResponse(String branchName) {
}
