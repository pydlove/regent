package com.pydlove.regent.bug.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** 缺陷列表读模型，额外带项目名。 */
@Data
public class BugRow {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long requirementId;
    private Long sourceTaskId;
    private Long fixTaskId;
    private String source;
    private String title;
    private String severity;
    private String status;
    private String reproduction;
    private String filePath;
    private String detail;
    private String wontFixReason;
    private LocalDateTime foundAt;
    private LocalDateTime fixedAt;
}
