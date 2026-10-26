package com.pydlove.regent.gitcommit.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

public record GitCommitRecordId(long value) {

    public GitCommitRecordId {
        if (value < 1) {
            throw new DomainException("Git 提交记录 ID 必须为正数：" + value);
        }
    }

    public static GitCommitRecordId of(long value) {
        return new GitCommitRecordId(value);
    }
}
