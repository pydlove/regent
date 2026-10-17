package com.pydlove.regent.plan.domain.repository;

import com.pydlove.regent.plan.domain.model.PlanId;
import com.pydlove.regent.plan.domain.model.PlanRun;
import com.pydlove.regent.plan.domain.model.PlanRunId;

import java.util.List;
import java.util.Optional;

/** 计划执行聚合仓储。 */
public interface PlanRunRepository {

    Optional<PlanRun> findById(PlanRunId id);

    Optional<PlanRun> findLatestByPlan(PlanId planId);

    List<PlanRun> listByPlan(PlanId planId);

    List<PlanRun> findRunning(int limit);

    void save(PlanRun run);
}
