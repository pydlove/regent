package com.pydlove.regent.plan.domain.repository;

import com.pydlove.regent.plan.domain.model.Plan;
import com.pydlove.regent.plan.domain.model.PlanId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 计划聚合仓储。 */
public interface PlanRepository {

    Optional<Plan> findById(PlanId id);

    /** 同一计划的人工触发与定时扫描可能并发，启动事务内用行锁重新读取。 */
    Optional<Plan> findByIdForUpdate(PlanId id);

    List<Plan> findAll();

    List<Plan> findDue(Instant now, int limit);

    void save(Plan plan);
}
