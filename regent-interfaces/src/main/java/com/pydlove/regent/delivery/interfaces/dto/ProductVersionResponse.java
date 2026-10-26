package com.pydlove.regent.delivery.interfaces.dto;

import com.pydlove.regent.delivery.domain.model.ProductVersion;

import java.time.Instant;

public record ProductVersionResponse(
        long id,
        long projectId,
        String versionNo,
        String name,
        String description,
        String status,
        Long workflowTemplateId,
        long createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductVersionResponse from(ProductVersion version) {
        return from(version, null);
    }

    public static ProductVersionResponse from(ProductVersion version, Long workflowTemplateId) {
        return new ProductVersionResponse(
                version.getId().value(),
                version.getProjectId().value(),
                version.getVersionNo(),
                version.getName(),
                version.getDescription(),
                version.getStatus().name(),
                workflowTemplateId,
                version.getCreatedBy().value(),
                version.getCreatedAt(),
                version.getUpdatedAt());
    }
}
