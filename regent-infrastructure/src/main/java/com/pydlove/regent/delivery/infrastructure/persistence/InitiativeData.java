package com.pydlove.regent.delivery.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InitiativeData {
    private Long id;
    private Long projectId;
    private Long versionId;
    private Long createdBy;
    private String title;
    private String businessGoal;
    private String scopeText;
    private String outOfScopeText;
    private String successMetrics;
    private String stakeholders;
    private String targetVersion;
    private String riskText;
    private String meetingMinutes;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
