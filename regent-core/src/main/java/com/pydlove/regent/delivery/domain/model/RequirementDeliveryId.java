package com.pydlove.regent.delivery.domain.model;

public record RequirementDeliveryId(long value) {
    public RequirementDeliveryId {
        if (value <= 0) throw new IllegalArgumentException("需求交付 ID 必须为正数");
    }

    public static RequirementDeliveryId of(long value) {
        return new RequirementDeliveryId(value);
    }
}
