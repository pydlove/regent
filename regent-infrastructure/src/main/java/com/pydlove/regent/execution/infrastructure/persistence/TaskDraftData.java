package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code task_draft} 表的数据对象。 */
@Data
public class TaskDraftData {

    private Long id;
    private Long userId;
    private Long projectId;
    private String prompt;
    private Integer priority;
    private String pluginIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
