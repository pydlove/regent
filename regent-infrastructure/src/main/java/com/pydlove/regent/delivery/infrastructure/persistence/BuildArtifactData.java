package com.pydlove.regent.delivery.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BuildArtifactData {
    private Long id;
    private Long projectId;
    private String version;
    private String gitCommit;
    private String digest;
    private Long sizeBytes;
    private String storageRef;
    private Long builtBy;
    private LocalDateTime builtAt;
}
