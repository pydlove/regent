package com.pydlove.regent.bug.application.query;

import com.pydlove.regent.bug.domain.repository.BugPage;
import com.pydlove.regent.bug.domain.repository.BugQuery;
import com.pydlove.regent.bug.domain.repository.BugQueryRepository;
import com.pydlove.regent.bug.domain.repository.BugView;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

/** 缺陷单读侧用例。 */
public class BugQueryService {

    private final BugQueryRepository bugQueryRepository;

    public BugQueryService(BugQueryRepository bugQueryRepository) {
        this.bugQueryRepository = bugQueryRepository;
    }

    public BugPage list(BugQuery query) {
        return bugQueryRepository.findPage(query);
    }

    public BugView get(long id) {
        return bugQueryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("缺陷单不存在：" + id));
    }
}
