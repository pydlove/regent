package com.pydlove.regent.gitcommit.domain.model;

/** 平台发起的一次 Git 提交尝试状态。 */
public enum GitCommitStatus {
    RUNNING,
    SUCCEEDED,
    FAILED
}
