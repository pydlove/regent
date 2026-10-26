package com.pydlove.regent.delivery.domain.model;

public record InitiativeId(long value) {
    public InitiativeId {
        if (value <= 0) throw new IllegalArgumentException("立项 ID 必须为正数");
    }

    public static InitiativeId of(long value) {
        return new InitiativeId(value);
    }
}
