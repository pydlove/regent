package com.pydlove.regent.gitcommit.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

public record GitCommitTaskRunId(long value) {

    public GitCommitTaskRunId {
        if (value < 1) {
            throw new DomainException("Git 提交执行记录 ID 必须为正数：" + value);
        }
    }

    public static GitCommitTaskRunId of(long value) {
        return new GitCommitTaskRunId(value);
    }
}
