package com.pydlove.regent.delivery.application.query;

import com.pydlove.regent.delivery.domain.model.DeliveryGate;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.model.RequirementDesignAttachment;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModule;

import java.util.List;

public record RequirementDeliveryView(
        RequirementDelivery delivery,
        String requirementTitle,
        String initiativeTitle,
        String projectName,
        List<DeliveryGate> gates,
        List<RequirementDesignModule> modules,
        List<RequirementDesignAttachment> attachments) {

    public RequirementDeliveryView(
            RequirementDelivery delivery,
            String requirementTitle,
            String initiativeTitle,
            String projectName,
            List<DeliveryGate> gates) {
        this(delivery, requirementTitle, initiativeTitle, projectName, gates,
                List.of(), List.of());
    }
}
