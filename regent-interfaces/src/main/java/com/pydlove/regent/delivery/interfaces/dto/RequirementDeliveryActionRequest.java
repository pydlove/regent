package com.pydlove.regent.delivery.interfaces.dto;

public record RequirementDeliveryActionRequest(
        String action,
        String comment,
        String designSummary,
        String designContent) {
}
