package com.pydlove.regent.gitcommit.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

public record GitCommitOperatorId(long value) {

    public GitCommitOperatorId {
        if (value < 1) {
            throw new DomainException("Git 提交操作者 ID 必须为正数：" + value);
        }
    }

    public static GitCommitOperatorId of(long value) {
        return new GitCommitOperatorId(value);
    }
}
