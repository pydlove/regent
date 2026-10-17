package com.pydlove.regent.plan.infrastructure.persistence;

import com.pydlove.regent.plan.domain.model.PlanId;
import com.pydlove.regent.plan.domain.model.PlanRun;
import com.pydlove.regent.plan.domain.model.PlanRunId;
import com.pydlove.regent.plan.domain.model.PlanRunItemSnapshot;
import com.pydlove.regent.plan.domain.repository.PlanRunRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 计划执行聚合的 MyBatis 仓储。 */
@Repository
public class MyBatisPlanRunRepository implements PlanRunRepository {

    private final PlanRunMapper mapper;

    public MyBatisPlanRunRepository(PlanRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<PlanRun> findById(PlanRunId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public Optional<PlanRun> findLatestByPlan(PlanId planId) {
        return Optional.ofNullable(mapper.selectLatestByPlan(planId.value())).map(this::toDomain);
    }

    @Override
    public List<PlanRun> listByPlan(PlanId planId) {
        return mapper.selectByPlan(planId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<PlanRun> findRunning(int limit) {
        return mapper.selectRunning(limit).stream().map(this::toDomain).toList();
    }

    @Override
    public void save(PlanRun run) {
        PlanRunData data = PlanRunDataMapper.toData(run);
        if (run.getId() == null) {
            mapper.insert(data);
            run.assignIdentity(PlanRunId.of(data.getId()));
            for (PlanRunItemSnapshot item : run.snapshot().items()) {
                mapper.insertItem(PlanRunDataMapper.toItemData(run.getId(), item));
            }
            return;
        }
        if (mapper.update(data) == 0) {
            throw new DomainException("计划执行未更新：" + run.getId());
        }
        for (PlanRunItemSnapshot item : run.snapshot().items()) {
            if (mapper.updateItem(PlanRunDataMapper.toItemData(run.getId(), item)) == 0) {
                throw new DomainException("计划执行任务结果未更新：" + item.taskId());
            }
        }
    }

    private PlanRun toDomain(PlanRunData data) {
        return PlanRunDataMapper.toDomain(data, mapper.selectItems(data.getId()));
    }
}
