package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code task_code_standard} 数据对象。 */
@Data
public class TaskCodeStandardData {
    private Long id;
    private Long taskId;
    private Long standardId;
    private Long standardVersionId;
    private String standardName;
    private Integer versionNo;
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private String contentHash;
    private String storagePath;
    private Long sourceProjectId;
    private LocalDateTime createdAt;
}
