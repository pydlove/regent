package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code task_adjustment} 表的数据对象。 */
@Data
public class TaskAdjustmentData {

    private Long id;
    private Long taskId;
    private String sourceType;
    private Long sourceTaskId;
    private String sourceTitle;
    private String content;
    private String status;
    private Long appliedRunId;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
}
