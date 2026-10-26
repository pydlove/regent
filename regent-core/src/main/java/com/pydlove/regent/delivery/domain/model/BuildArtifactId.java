package com.pydlove.regent.delivery.domain.model;

public record BuildArtifactId(long value) {
    public BuildArtifactId {
        if (value <= 0) throw new IllegalArgumentException("制品 ID 必须为正数");
    }

    public static BuildArtifactId of(long value) {
        return new BuildArtifactId(value);
    }
}
