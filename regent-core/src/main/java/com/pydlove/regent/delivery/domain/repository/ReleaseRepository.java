package com.pydlove.regent.delivery.domain.repository;

import com.pydlove.regent.delivery.domain.model.Release;
import com.pydlove.regent.delivery.domain.model.ReleaseId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;
import java.util.Optional;

public interface ReleaseRepository {
    Optional<Release> findById(ReleaseId id);

    List<Release> findByProject(ProjectId projectId);

    int nextAttemptNo(ProjectId projectId, String version);

    void save(Release release);
}
