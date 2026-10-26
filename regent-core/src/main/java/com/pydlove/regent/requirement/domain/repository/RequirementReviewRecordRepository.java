package com.pydlove.regent.requirement.domain.repository;

import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.requirement.domain.model.RequirementReviewRecord;
import com.pydlove.regent.requirement.domain.model.RequirementReviewRecordId;

import java.util.List;
import java.util.Optional;

/** 线下需求评审记录仓储。 */
public interface RequirementReviewRecordRepository {

    Optional<RequirementReviewRecord> findById(RequirementReviewRecordId id);

    List<RequirementReviewRecord> findByVersion(ProductVersionId versionId);

    int nextReviewNo(ProductVersionId versionId);

    void save(RequirementReviewRecord record);

    void update(RequirementReviewRecord record);
}
