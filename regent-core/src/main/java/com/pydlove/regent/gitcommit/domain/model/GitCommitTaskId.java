package com.pydlove.regent.gitcommit.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

public record GitCommitTaskId(long value) {

    public GitCommitTaskId {
        if (value < 1) {
            throw new DomainException("Git 提交任务 ID 必须为正数：" + value);
        }
    }

    public static GitCommitTaskId of(long value) {
        return new GitCommitTaskId(value);
    }
}
