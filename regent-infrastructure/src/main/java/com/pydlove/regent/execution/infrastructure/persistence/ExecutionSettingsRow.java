package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code execution_setting} 单行表的数据对象。 */
@Data
public class ExecutionSettingsRow {

    private Integer id;
    private Integer maxConcurrentTasks;
    private LocalDateTime updatedAt;
}
