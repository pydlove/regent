package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code task_attachment} 表的数据对象，字段与列一一对应。 */
@Data
public class TaskAttachmentData {

    private Long id;
    private Long taskId;
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private String storagePath;
    private LocalDateTime createdAt;
}
