package com.pydlove.regent.requirement.interfaces.dto;

import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementTrashItem;

import java.time.Instant;

public record RequirementTrashResponse(
        long id,
        long projectId,
        Long versionId,
        String title,
        String rawText,
        String source,
        Instant deletedAt,
        Long deletedBy) {

    public static RequirementTrashResponse from(RequirementTrashItem item) {
        Requirement requirement = item.requirement();
        return new RequirementTrashResponse(
                requirement.getId().value(),
                requirement.getProjectId().value(),
                requirement.getVersionId() == null
                        ? null : requirement.getVersionId().value(),
                requirement.getTitle(),
                requirement.getRawText(),
                requirement.getSource().name(),
                item.deletedAt(),
                item.deletedBy() == null ? null : item.deletedBy().value());
    }
}
