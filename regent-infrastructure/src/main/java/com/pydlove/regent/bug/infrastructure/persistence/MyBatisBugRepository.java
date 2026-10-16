package com.pydlove.regent.bug.infrastructure.persistence;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.bug.domain.repository.BugRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 缺陷单写侧仓储实现。 */
@Repository
public class MyBatisBugRepository implements BugRepository {

    private final BugMapper bugMapper;

    public MyBatisBugRepository(BugMapper bugMapper) {
        this.bugMapper = bugMapper;
    }

    @Override
    public Optional<Bug> findById(BugId id) {
        return Optional.ofNullable(bugMapper.selectDataById(id.value()))
                .map(BugDataMapper::toDomain);
    }

    @Override
    public Optional<Bug> findByIdForUpdate(BugId id) {
        return Optional.ofNullable(bugMapper.selectDataByIdForUpdate(id.value()))
                .map(BugDataMapper::toDomain);
    }

    @Override
    public void save(Bug bug) {
        BugData data = BugDataMapper.toData(bug);
        if (bug.getId() == null) {
            bugMapper.insert(data);
            bug.assignIdentity(BugId.of(data.getId()));
            return;
        }
        int affected = bugMapper.update(data);
        if (affected == 0) {
            throw new DomainException("缺陷单未更新，可能已被删除：" + bug.getId());
        }
    }
}
