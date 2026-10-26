package com.pydlove.regent.delivery.interfaces.dto;

import com.pydlove.regent.delivery.domain.model.BuildArtifact;

import java.time.Instant;

public record BuildArtifactResponse(
        long id,
        String version,
        String gitCommit,
        String digest,
        long sizeBytes,
        String storageRef,
        Instant builtAt) {

    public static BuildArtifactResponse from(BuildArtifact artifact) {
        return new BuildArtifactResponse(
                artifact.getId().value(),
                artifact.getVersion(),
                artifact.getGitCommit(),
                artifact.getDigest(),
                artifact.getSizeBytes(),
                artifact.getStorageRef(),
                artifact.getBuiltAt());
    }
}
