package com.pydlove.regent.delivery.domain.model;

public record ReleaseId(long value) {
    public ReleaseId {
        if (value <= 0) throw new IllegalArgumentException("发布 ID 必须为正数");
    }

    public static ReleaseId of(long value) {
        return new ReleaseId(value);
    }
}
