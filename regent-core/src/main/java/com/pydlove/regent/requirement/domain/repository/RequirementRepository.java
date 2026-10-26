package com.pydlove.regent.requirement.domain.repository;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.requirement.domain.model.RequirementTrashItem;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 需求聚合仓储。 */
public interface RequirementRepository {

    Optional<Requirement> findById(RequirementId id);

    default Optional<Requirement> findByIdOwned(RequirementId id, UserId ownerId) {
        return findById(id);
    }

    List<Requirement> findByProject(ProjectId projectId);

    default List<Requirement> findByProject(ProjectId projectId, UserId ownerId) {
        return findByProject(projectId);
    }

    List<Requirement> findAll(UserId ownerId);

    List<RequirementTrashItem> findDeletedByProject(ProjectId projectId, UserId ownerId);

    Optional<RequirementTrashItem> findDeletedByIdOwned(RequirementId id, UserId ownerId);

    void moveToTrash(RequirementId id, UserId deletedBy, Instant deletedAt);

    void restore(RequirementId id, Instant updatedAt);

    void save(Requirement requirement);
}
