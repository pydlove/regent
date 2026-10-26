package com.pydlove.regent.organization.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrganizationAuditData {
    private Long id;
    private Long organizationId;
    private Long actorId;
    private String action;
    private String targetType;
    private Long targetId;
    private String detailJson;
    private LocalDateTime createdAt;
}
