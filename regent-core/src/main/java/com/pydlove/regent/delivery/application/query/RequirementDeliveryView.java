package com.pydlove.regent.delivery.application.query;

import com.pydlove.regent.delivery.domain.model.DeliveryGate;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;

import java.util.List;

public record RequirementDeliveryView(
        RequirementDelivery delivery,
        String requirementTitle,
        String initiativeTitle,
        String projectName,
        List<DeliveryGate> gates) {
}
