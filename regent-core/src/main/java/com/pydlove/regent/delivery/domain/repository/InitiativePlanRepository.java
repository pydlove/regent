package com.pydlove.regent.delivery.domain.repository;

import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.InitiativePlan;

import java.util.Optional;

/** 版本计划仓储。 */
public interface InitiativePlanRepository {

    Optional<InitiativePlan> findByInitiative(InitiativeId initiativeId);

    void save(InitiativePlan plan);
}
