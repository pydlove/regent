package com.pydlove.regent.gitcommit.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

/** Git 提交聚合的 MyBatis 映射。 */
public interface GitCommitRecordMapper {

    GitCommitRecordData selectById(@Param("id") long id);

    GitCommitRecordData selectSucceededBySnapshot(@Param("snapshotId") long snapshotId);

    GitCommitRecordData selectRunningBySnapshot(@Param("snapshotId") long snapshotId);

    int insert(GitCommitRecordData data);

    int update(GitCommitRecordData data);
}
