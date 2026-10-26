package com.pydlove.regent.project.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectMemberData {
    private Long id;
    private Long projectId;
    private Long userId;
    private String roleCode;
    private LocalDateTime createdAt;
}
