package com.pydlove.regent.delivery.interfaces.dto;

import com.pydlove.regent.delivery.application.query.RequirementDeliveryView;

public record ReleaseDeliveryResponse(
        long id,
        long requirementId,
        String requirementTitle,
        String stage,
        String status) {

    public static ReleaseDeliveryResponse from(RequirementDeliveryView view) {
        return new ReleaseDeliveryResponse(
                view.delivery().getId().value(),
                view.delivery().getRequirementId().value(),
                view.requirementTitle(),
                view.delivery().getStage().name(),
                view.delivery().getStatus().name());
    }
}
