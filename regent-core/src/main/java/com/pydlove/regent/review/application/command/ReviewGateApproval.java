package com.pydlove.regent.review.application.command;

/** 部署门禁通过后允许引用的审阅事实。 */
public record ReviewGateApproval(
        long taskId,
        long projectId,
        long snapshotId,
        long decisionId) {
}
