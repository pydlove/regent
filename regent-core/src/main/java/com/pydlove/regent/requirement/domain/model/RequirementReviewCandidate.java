package com.pydlove.regent.requirement.domain.model;

/** 进入某一轮需求评审的需求快照。 */
public record RequirementReviewCandidate(
        RequirementId requirementId,
        long requirementNo,
        String title) {

    public RequirementReviewCandidate {
        if (requirementId == null) {
            throw new com.pydlove.regent.shared.domain.DomainException("评审需求不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new com.pydlove.regent.shared.domain.DomainException("评审需求标题不能为空");
        }
        title = title.trim();
    }
}
