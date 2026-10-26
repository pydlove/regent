package com.pydlove.regent.delivery.domain.repository;

import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RequirementDeliveryRepository {
    Optional<RequirementDelivery> findById(RequirementDeliveryId id);

    List<RequirementDelivery> findByProject(ProjectId projectId);

    List<RequirementDelivery> findByIds(Collection<RequirementDeliveryId> ids);

    Optional<RequirementDelivery> findActiveByRequirement(RequirementId requirementId);

    boolean existsStartedDesignForVersion(ProductVersionId versionId);

    void save(RequirementDelivery delivery);
}
