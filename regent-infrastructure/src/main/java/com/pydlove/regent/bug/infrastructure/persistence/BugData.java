package com.pydlove.regent.bug.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** bug 表的数据对象。时间按项目约定存 UTC 的 DATETIME 字面量。 */
@Data
public class BugData {
    private Long id;
    private Long projectId;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
