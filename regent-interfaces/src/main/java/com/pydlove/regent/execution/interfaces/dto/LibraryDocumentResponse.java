package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.LibraryDocument;

import java.time.Instant;

/** 项目文件库文档对外契约。 */
public record LibraryDocumentResponse(
        long id,
        long projectId,
        String libraryType,
        Long sourceTaskId,
        String filename,
        String contentType,
        long sizeBytes,
        String contentUrl,
        Instant createdAt) {

    public static LibraryDocumentResponse from(LibraryDocument document) {
        return new LibraryDocumentResponse(
                document.id().value(),
                document.projectId().value(),
                document.libraryType().name(),
                document.sourceTaskId() == null ? null : document.sourceTaskId().value(),
                document.filename(),
                document.contentType(),
                document.sizeBytes(),
                "/api/file-library/" + document.id().value() + "/content",
                document.createdAt());
    }
}
