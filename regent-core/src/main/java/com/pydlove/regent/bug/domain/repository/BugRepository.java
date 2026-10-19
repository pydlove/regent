package com.pydlove.regent.bug.domain.repository;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.identity.domain.model.UserId;

import java.util.Optional;

/** 缺陷单写侧仓储。 */
public interface BugRepository {

    Optional<Bug> findById(BugId id);

    default Optional<Bug> findByIdOwned(BugId id, UserId ownerId) {
        return findById(id);
    }

    /** 取缺陷单并锁行，供会创建一个新聚合的写用例做并发守卫。 */
    Optional<Bug> findByIdForUpdate(BugId id);

    void save(Bug bug);
}
