package com.pydlove.regent.organization.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrganizationMemberData {
    private Long id;
    private Long organizationId;
    private Long userId;
    private String roleCode;
    private LocalDateTime joinedAt;
}
