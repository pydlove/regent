package com.pydlove.regent.delivery.domain.model;

public record DeliveryGateId(long value) {
    public DeliveryGateId {
        if (value <= 0) throw new IllegalArgumentException("门禁记录 ID 必须为正数");
    }

    public static DeliveryGateId of(long value) {
        return new DeliveryGateId(value);
    }
}
