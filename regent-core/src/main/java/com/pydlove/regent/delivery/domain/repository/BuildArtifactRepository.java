package com.pydlove.regent.delivery.domain.repository;

import com.pydlove.regent.delivery.domain.model.BuildArtifact;
import com.pydlove.regent.delivery.domain.model.BuildArtifactId;

import java.util.Optional;

public interface BuildArtifactRepository {
    Optional<BuildArtifact> findById(BuildArtifactId id);

    void save(BuildArtifact artifact);
}
