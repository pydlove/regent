package com.pydlove.regent.delivery.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RequirementDeliveryData {
    private Long id;
    private Long initiativeId;
    private Long projectId;
    private Long requirementId;
    private Long createdBy;
    private String stage;
    private String status;
    private String designSummary;
    private String designContent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
