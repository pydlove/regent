package com.pydlove.regent.support;

import com.pydlove.regent.plan.domain.model.Plan;
import com.pydlove.regent.plan.domain.model.PlanId;
import com.pydlove.regent.plan.domain.model.PlanRun;
import com.pydlove.regent.plan.domain.model.PlanRunId;
import com.pydlove.regent.plan.domain.repository.PlanRepository;
import com.pydlove.regent.plan.domain.repository.PlanRunRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 计划上下文的内存仓储替身。 */
public final class PlanFakes {

    private PlanFakes() {
    }

    public static final class Plans implements PlanRepository {

        private final Map<Long, Plan> store = new LinkedHashMap<>();
        private long sequence;

        public Plan get(long id) {
            return store.get(id);
        }

        @Override
        public Optional<Plan> findById(PlanId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<Plan> findByIdForUpdate(PlanId id) {
            return findById(id);
        }

        @Override
        public List<Plan> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public List<Plan> findDue(Instant now, int limit) {
            return store.values().stream()
                    .filter(plan -> plan.isDueAt(now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void save(Plan plan) {
            if (plan.getId() == null) {
                plan.assignIdentity(PlanId.of(++sequence));
            }
            store.put(plan.getId().value(), plan);
        }
    }

    public static final class Runs implements PlanRunRepository {

        private final Map<Long, PlanRun> store = new LinkedHashMap<>();
        private long sequence;

        public PlanRun get(long id) {
            return store.get(id);
        }

        @Override
        public Optional<PlanRun> findById(PlanRunId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<PlanRun> findLatestByPlan(PlanId planId) {
            return store.values().stream()
                    .filter(run -> run.getPlanId().equals(planId))
                    .max(Comparator.comparing(run -> run.getId().value()));
        }

        @Override
        public List<PlanRun> listByPlan(PlanId planId) {
            return store.values().stream()
                    .filter(run -> run.getPlanId().equals(planId))
                    .sorted(Comparator.comparingLong((PlanRun run) -> run.getId().value()).reversed())
                    .toList();
        }

        @Override
        public List<PlanRun> findRunning(int limit) {
            return store.values().stream()
                    .filter(run -> run.getStatus() == com.pydlove.regent.plan.domain.model.PlanRunStatus.RUNNING)
                    .limit(limit)
                    .toList();
        }

        @Override
        public void save(PlanRun run) {
            if (run.getId() == null) {
                run.assignIdentity(PlanRunId.of(++sequence));
            }
            store.put(run.getId().value(), run);
        }
    }
}
