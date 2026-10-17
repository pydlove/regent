package com.pydlove.regent.plan.infrastructure.persistence;

import com.pydlove.regent.plan.domain.model.Plan;
import com.pydlove.regent.plan.domain.model.PlanId;
import com.pydlove.regent.plan.domain.repository.PlanRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 计划聚合的 MyBatis 仓储。 */
@Repository
public class MyBatisPlanRepository implements PlanRepository {

    private final PlanMapper mapper;

    public MyBatisPlanRepository(PlanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Plan> findById(PlanId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public Optional<Plan> findByIdForUpdate(PlanId id) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(id.value())).map(this::toDomain);
    }

    @Override
    public List<Plan> findAll() {
        return mapper.selectAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Plan> findDue(Instant now, int limit) {
        return mapper.selectDue(toDb(now), limit).stream().map(this::toDomain).toList();
    }

    @Override
    public void save(Plan plan) {
        PlanData data = PlanDataMapper.toData(plan);
        if (plan.getId() == null) {
            mapper.insert(data);
            plan.assignIdentity(PlanId.of(data.getId()));
            for (int position = 0; position < plan.getTaskIds().size(); position++) {
                PlanTaskData task = new PlanTaskData();
                task.setPlanId(plan.getId().value());
                task.setTaskId(plan.getTaskIds().get(position).value());
                task.setSortOrder(position);
                task.setCreatedAt(toDb(plan.getCreatedAt()));
                mapper.insertTask(task);
            }
            return;
        }
        if (mapper.update(data) == 0) {
            throw new DomainException("计划未更新：" + plan.getId());
        }
    }

    private Plan toDomain(PlanData data) {
        return PlanDataMapper.toDomain(data, mapper.selectTaskIds(data.getId()));
    }

    private static LocalDateTime toDb(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
