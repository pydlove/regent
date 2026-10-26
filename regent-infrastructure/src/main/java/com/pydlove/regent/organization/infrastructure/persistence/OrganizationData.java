package com.pydlove.regent.organization.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrganizationData {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
