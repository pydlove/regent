package com.pydlove.regent.organization.interfaces.dto;

import com.pydlove.regent.organization.application.query.OrganizationView;
import com.pydlove.regent.organization.domain.model.OrganizationRole;

import java.time.Instant;
import java.util.List;

public record OrganizationResponse(
        long id,
        String code,
        String name,
        String description,
        String status,
        List<String> currentUserRoles,
        int memberCount,
        long projectCount,
        Instant createdAt) {

    public static OrganizationResponse from(OrganizationView view) {
        return new OrganizationResponse(
                view.organization().getId().value(),
                view.organization().getCode(),
                view.organization().getName(),
                view.organization().getDescription(),
                view.organization().getStatus().name(),
                view.currentUserRoles().stream().map(OrganizationRole::name).toList(),
                view.memberCount(),
                view.projectCount(),
                view.organization().getCreatedAt());
    }
}
