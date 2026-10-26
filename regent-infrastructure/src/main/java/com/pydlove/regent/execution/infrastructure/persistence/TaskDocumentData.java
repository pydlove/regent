package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code task_document} 数据对象。 */
@Data
public class TaskDocumentData {
    private Long id;
    private Long taskId;
    private Long libraryDocumentId;
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private String storagePath;
    private LocalDateTime createdAt;
}
