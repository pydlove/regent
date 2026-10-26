package com.pydlove.regent.delivery.domain.repository;

import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;
import java.util.Optional;

public interface InitiativeRepository {
    Optional<Initiative> findById(InitiativeId id);

    List<Initiative> findByProject(ProjectId projectId);

    Optional<Initiative> findByVersion(ProductVersionId versionId);

    void save(Initiative initiative);
}
