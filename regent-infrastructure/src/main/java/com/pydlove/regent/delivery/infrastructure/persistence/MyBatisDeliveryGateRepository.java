package com.pydlove.regent.delivery.infrastructure.persistence;

import com.pydlove.regent.delivery.domain.model.DeliveryGate;
import com.pydlove.regent.delivery.domain.model.DeliveryGateId;
import com.pydlove.regent.delivery.domain.model.GateDecision;
import com.pydlove.regent.delivery.domain.model.GateScope;
import com.pydlove.regent.delivery.domain.model.GateType;
import com.pydlove.regent.delivery.domain.repository.DeliveryGateRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MyBatisDeliveryGateRepository implements DeliveryGateRepository {

    private final DeliveryGateMapper mapper;

    public MyBatisDeliveryGateRepository(DeliveryGateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DeliveryGate save(DeliveryGate gate) {
        DeliveryGateData data = new DeliveryGateData();
        data.setScopeType(gate.scope().name());
        data.setScopeId(gate.scopeId());
        data.setGateType(gate.type().name());
        data.setDecision(gate.decision().name());
        data.setRoleCode(gate.roleCode());
        data.setComment(gate.comment());
        data.setDecidedBy(gate.decidedBy().value());
        data.setDecidedAt(DeliveryTime.toDb(gate.decidedAt()));
        if (mapper.insert(data) != 1) throw new DomainException("门禁记录新增失败");
        return gate.withId(DeliveryGateId.of(data.getId()));
    }

    @Override
    public List<DeliveryGate> findByScope(GateScope scope, long scopeId) {
        return mapper.selectByScope(scope.name(), scopeId).stream()
                .map(data -> new DeliveryGate(
                        DeliveryGateId.of(data.getId()),
                        GateScope.valueOf(data.getScopeType()),
                        data.getScopeId(),
                        GateType.valueOf(data.getGateType()),
                        GateDecision.valueOf(data.getDecision()),
                        data.getRoleCode(),
                        data.getComment(),
                        UserId.of(data.getDecidedBy()),
                        DeliveryTime.fromDb(data.getDecidedAt())))
                .toList();
    }
}
