package com.pydlove.regent.gitcommit.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

public record GitCommitProjectId(long value) {

    public GitCommitProjectId {
        if (value < 1) {
            throw new DomainException("Git 提交项目 ID 必须为正数：" + value);
        }
    }

    public static GitCommitProjectId of(long value) {
        return new GitCommitProjectId(value);
    }
}
