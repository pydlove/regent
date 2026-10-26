package com.pydlove.regent.delivery.domain.repository;

import com.pydlove.regent.delivery.domain.model.DeliveryGate;
import com.pydlove.regent.delivery.domain.model.GateScope;

import java.util.List;

public interface DeliveryGateRepository {
    DeliveryGate save(DeliveryGate gate);

    List<DeliveryGate> findByScope(GateScope scope, long scopeId);
}
