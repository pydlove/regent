package com.pydlove.regent.bug.domain.repository;

import java.util.Optional;

/** 缺陷单读侧仓储。 */
public interface BugQueryRepository {

    BugPage findPage(BugQuery query);

    Optional<BugView> findById(long id);
}
