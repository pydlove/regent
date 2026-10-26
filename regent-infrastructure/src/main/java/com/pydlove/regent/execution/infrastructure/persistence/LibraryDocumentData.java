package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code file_library_document} 数据对象。 */
@Data
public class LibraryDocumentData {
    private Long id;
    private Long projectId;
    private String libraryType;
    private Long sourceTaskId;
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private String contentHash;
    private String storagePath;
    private LocalDateTime createdAt;
}
