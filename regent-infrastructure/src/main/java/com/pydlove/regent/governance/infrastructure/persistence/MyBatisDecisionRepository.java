package com.pydlove.regent.governance.infrastructure.persistence;

import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 决策单聚合仓储的 MyBatis 实现。 */
@Repository
public class MyBatisDecisionRepository implements DecisionRepository {

    private final DecisionMapper decisionMapper;

    public MyBatisDecisionRepository(DecisionMapper decisionMapper) {
        this.decisionMapper = decisionMapper;
    }

    @Override
    public Optional<Decision> findById(DecisionId id) {
        return Optional.ofNullable(decisionMapper.selectById(id.value()))
                .map(DecisionDataMapper::toDomain);
    }

    @Override
    public List<Decision> findPendingByTaskForUpdate(TaskId taskId) {
        return decisionMapper.selectPendingByTaskForUpdate(taskId.value()).stream()
                .map(DecisionDataMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Decision> findLatestResolvedByTask(TaskId taskId) {
        return Optional.ofNullable(decisionMapper.selectLatestResolvedByTask(taskId.value()))
                .map(DecisionDataMapper::toDomain);
    }

    @Override
    public void save(Decision decision) {
        DecisionData data = DecisionDataMapper.toData(decision);
        if (decision.getId() == null) {
            decisionMapper.insert(data);
            decision.assignIdentity(DecisionId.of(data.getId()));
            return;
        }
        int affected = decisionMapper.update(data);
        if (affected == 0) {
            throw new DomainException("决策未更新，可能已被删除：" + decision.getId());
        }
    }
}
