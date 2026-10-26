package com.pydlove.regent.gitcommit.infrastructure.persistence;

import com.pydlove.regent.gitcommit.domain.model.GitCommitRecord;
import com.pydlove.regent.gitcommit.domain.model.GitCommitRecordId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitSnapshotId;
import com.pydlove.regent.gitcommit.domain.repository.GitCommitRecordRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Git 提交聚合仓储的 MyBatis 实现。 */
@Repository
public class MyBatisGitCommitRecordRepository implements GitCommitRecordRepository {

    private final GitCommitRecordMapper mapper;

    public MyBatisGitCommitRecordRepository(GitCommitRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public GitCommitRecord save(GitCommitRecord record) {
        GitCommitRecordData data = GitCommitRecordDataMapper.toData(record);
        if (record.getId() == null) {
            if (mapper.insert(data) != 1) {
                throw new DomainException("Git 提交记录新增失败");
            }
            record.assignIdentity(GitCommitRecordId.of(data.getId()));
        } else if (mapper.update(data) != 1) {
            throw new DomainException("Git 提交记录更新失败：" + record.getId().value());
        }
        return record;
    }

    @Override
    public Optional<GitCommitRecord> findById(GitCommitRecordId id) {
        return Optional.ofNullable(mapper.selectById(id.value()))
                .map(GitCommitRecordDataMapper::toDomain);
    }

    @Override
    public Optional<GitCommitRecord> findSucceededBySnapshot(GitCommitSnapshotId snapshotId) {
        return Optional.ofNullable(mapper.selectSucceededBySnapshot(snapshotId.value()))
                .map(GitCommitRecordDataMapper::toDomain);
    }

    @Override
    public Optional<GitCommitRecord> findRunningBySnapshot(GitCommitSnapshotId snapshotId) {
        return Optional.ofNullable(mapper.selectRunningBySnapshot(snapshotId.value()))
                .map(GitCommitRecordDataMapper::toDomain);
    }
}
