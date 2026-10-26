package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.TaskDocument;

/** 任务文档对外契约，不暴露平台存储路径。 */
public record TaskDocumentResponse(
        long id,
        long taskId,
        Long libraryDocumentId,
        String filename,
        String contentType,
        long sizeBytes,
        String contentUrl,
        boolean savedToLibrary) {

    public static TaskDocumentResponse from(TaskDocument document) {
        return new TaskDocumentResponse(
                document.id().value(),
                document.taskId().value(),
                document.libraryDocumentId() == null ? null : document.libraryDocumentId().value(),
                document.filename(),
                document.contentType(),
                document.sizeBytes(),
                "/api/documents/" + document.id().value() + "/content",
                document.libraryDocumentId() != null);
    }
}
