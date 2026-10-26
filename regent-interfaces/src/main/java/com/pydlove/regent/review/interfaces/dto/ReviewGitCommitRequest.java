package com.pydlove.regent.review.interfaces.dto;

/** 审阅通过后的最终 Git 提交说明；Bug 编号由任务上下文自动生成。 */
public record ReviewGitCommitRequest(String message) {
}
