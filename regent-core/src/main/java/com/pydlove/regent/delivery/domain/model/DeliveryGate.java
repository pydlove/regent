package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 一次阶段门禁事实。只追加，不修改历史。 */
public record DeliveryGate(
        DeliveryGateId id,
        GateScope scope,
        long scopeId,
        GateType type,
        GateDecision decision,
        String roleCode,
        String comment,
        UserId decidedBy,
        Instant decidedAt) {

    public DeliveryGate {
        if (scope == null || type == null || decision == null || decidedBy == null) {
            throw new DomainException("门禁关键字段不能为空");
        }
        roleCode = roleCode == null || roleCode.isBlank() ? "OWNER" : roleCode.trim();
        if (scopeId <= 0) throw new DomainException("门禁对象 ID 必须为正数");
        comment = comment == null || comment.isBlank() ? null : comment.trim();
        decidedAt = decidedAt == null ? Instant.now().truncatedTo(ChronoUnit.MILLIS) : decidedAt;
    }

    public static DeliveryGate record(GateScope scope,
                                      long scopeId,
                                      GateType type,
                                      GateDecision decision,
                                      String roleCode,
                                      String comment,
                                      UserId decidedBy) {
        return new DeliveryGate(null, scope, scopeId, type, decision, roleCode, comment, decidedBy,
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    public DeliveryGate withId(DeliveryGateId assigned) {
        return new DeliveryGate(assigned, scope, scopeId, type, decision, roleCode, comment, decidedBy, decidedAt);
    }
}
