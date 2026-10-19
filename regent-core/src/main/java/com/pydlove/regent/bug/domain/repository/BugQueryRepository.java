package com.pydlove.regent.bug.domain.repository;

import com.pydlove.regent.identity.domain.model.UserId;

import java.util.Optional;

/** 缺陷单读侧仓储。 */
public interface BugQueryRepository {

    BugPage findPage(BugQuery query);

    Optional<BugView> findById(long id);

    default Optional<BugView> findByIdOwned(long id, UserId ownerId) {
        return findById(id);
    }
}
