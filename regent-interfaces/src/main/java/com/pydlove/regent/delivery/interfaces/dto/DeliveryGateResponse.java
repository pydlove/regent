package com.pydlove.regent.delivery.interfaces.dto;

import com.pydlove.regent.delivery.domain.model.DeliveryGate;

import java.time.Instant;

public record DeliveryGateResponse(
        long id,
        String scope,
        long scopeId,
        String type,
        String decision,
        String roleCode,
        String comment,
        long decidedBy,
        Instant decidedAt) {

    public static DeliveryGateResponse from(DeliveryGate gate) {
        return new DeliveryGateResponse(
                gate.id().value(),
                gate.scope().name(),
                gate.scopeId(),
                gate.type().name(),
                gate.decision().name(),
                gate.roleCode(),
                gate.comment(),
                gate.decidedBy().value(),
                gate.decidedAt());
    }
}
