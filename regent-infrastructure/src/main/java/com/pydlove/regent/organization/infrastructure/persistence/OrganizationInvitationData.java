package com.pydlove.regent.organization.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrganizationInvitationData {
    private Long id;
    private Long organizationId;
    private String email;
    private String roleCode;
    private String tokenHash;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
    private Long acceptedBy;
    private Long createdBy;
    private LocalDateTime createdAt;
}
