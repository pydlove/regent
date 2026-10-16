package com.pydlove.regent.governance.application.query;

import com.pydlove.regent.governance.domain.repository.DecisionPage;
import com.pydlove.regent.governance.domain.repository.DecisionQuery;
import com.pydlove.regent.governance.domain.repository.DecisionQueryRepository;
import com.pydlove.regent.governance.domain.repository.DecisionView;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

/** 决策单读侧用例。 */
public class DecisionQueryService {

    private final DecisionQueryRepository decisionQueryRepository;

    public DecisionQueryService(DecisionQueryRepository decisionQueryRepository) {
        this.decisionQueryRepository = decisionQueryRepository;
    }

    public DecisionPage list(DecisionQuery query) {
        return decisionQueryRepository.findPage(query);
    }

    public DecisionView get(long id) {
        return decisionQueryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("决策不存在：" + id));
    }
}
