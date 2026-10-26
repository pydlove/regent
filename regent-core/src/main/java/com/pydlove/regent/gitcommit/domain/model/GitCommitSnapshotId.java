package com.pydlove.regent.gitcommit.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

public record GitCommitSnapshotId(long value) {

    public GitCommitSnapshotId {
        if (value < 1) {
            throw new DomainException("Git 提交审阅快照 ID 必须为正数：" + value);
        }
    }

    public static GitCommitSnapshotId of(long value) {
        return new GitCommitSnapshotId(value);
    }
}
