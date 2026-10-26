package com.pydlove.regent.requirement.domain.repository;

import com.pydlove.regent.requirement.domain.model.RequirementReviewAttachment;
import com.pydlove.regent.requirement.domain.model.RequirementReviewAttachmentId;
import com.pydlove.regent.requirement.domain.model.RequirementReviewRecordId;

import java.util.List;
import java.util.Optional;

/** 需求评审纪要附件仓储。 */
public interface RequirementReviewAttachmentRepository {

    RequirementReviewAttachment save(RequirementReviewAttachment attachment);

    Optional<RequirementReviewAttachment> findById(RequirementReviewAttachmentId id);

    List<RequirementReviewAttachment> findByRecord(RequirementReviewRecordId recordId);
}
