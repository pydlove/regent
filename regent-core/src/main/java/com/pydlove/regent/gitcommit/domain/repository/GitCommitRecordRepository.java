package com.pydlove.regent.gitcommit.domain.repository;

import com.pydlove.regent.gitcommit.domain.model.GitCommitRecord;
import com.pydlove.regent.gitcommit.domain.model.GitCommitRecordId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitSnapshotId;

import java.util.Optional;

/** Git 提交聚合仓储。列表查询走独立只读查询仓储。 */
public interface GitCommitRecordRepository {

    GitCommitRecord save(GitCommitRecord record);

    Optional<GitCommitRecord> findById(GitCommitRecordId id);

    Optional<GitCommitRecord> findSucceededBySnapshot(GitCommitSnapshotId snapshotId);

    Optional<GitCommitRecord> findRunningBySnapshot(GitCommitSnapshotId snapshotId);
}
