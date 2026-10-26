package com.pydlove.regent.delivery.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReleaseData {
    private Long id;
    private Long projectId;
    private String version;
    private Integer attemptNo;
    private Long buildArtifactId;
    private String stage;
    private String status;
    private String releaseNotes;
    private String rollbackPlan;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
