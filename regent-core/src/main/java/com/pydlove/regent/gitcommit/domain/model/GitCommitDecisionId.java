package com.pydlove.regent.gitcommit.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

public record GitCommitDecisionId(long value) {

    public GitCommitDecisionId {
        if (value < 1) {
            throw new DomainException("Git 提交审阅结论 ID 必须为正数：" + value);
        }
    }

    public static GitCommitDecisionId of(long value) {
        return new GitCommitDecisionId(value);
    }
}
