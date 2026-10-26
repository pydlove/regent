package com.pydlove.regent.delivery.interfaces.dto;

import com.pydlove.regent.delivery.application.query.RequirementDeliveryView;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;

import java.time.Instant;
import java.util.List;

public record RequirementDeliveryResponse(
        long id,
        long initiativeId,
        long projectId,
        long requirementId,
        String requirementTitle,
        String initiativeTitle,
        String projectName,
        String stage,
        String status,
        String designSummary,
        String designContent,
        Instant createdAt,
        Instant updatedAt,
        List<DeliveryGateResponse> gates) {

    public static RequirementDeliveryResponse from(RequirementDeliveryView view) {
        RequirementDelivery delivery = view.delivery();
        return new RequirementDeliveryResponse(
                delivery.getId().value(),
                delivery.getInitiativeId().value(),
                delivery.getProjectId().value(),
                delivery.getRequirementId().value(),
                view.requirementTitle(),
                view.initiativeTitle(),
                view.projectName(),
                delivery.getStage().name(),
                delivery.getStatus().name(),
                delivery.getDesignSummary(),
                delivery.getDesignContent(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt(),
                view.gates().stream().map(DeliveryGateResponse::from).toList());
    }
}
