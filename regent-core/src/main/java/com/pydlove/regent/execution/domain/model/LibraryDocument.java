package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/** 项目文件库中的可复用文档；同一项目按内容哈希只保留一份。 */
public record LibraryDocument(
        LibraryDocumentId id,
        ProjectId projectId,
        LibraryType libraryType,
        TaskId sourceTaskId,
        String filename,
        String contentType,
        long sizeBytes,
        String contentHash,
        String storagePath,
        Instant createdAt) {

    public LibraryDocument {
        projectId = requireNotNull(projectId, "文件库所属项目");
        libraryType = libraryType == null ? LibraryType.GENERAL : libraryType;
        filename = requireText(filename, "文档文件名");
        contentType = requireText(contentType, "文档类型");
        if (sizeBytes <= 0) {
            throw new DomainException("文档大小必须为正：" + sizeBytes);
        }
        contentHash = requireText(contentHash, "文档内容摘要");
        if (contentHash.length() != 64) {
            throw new DomainException("文档内容摘要必须是 SHA-256");
        }
        storagePath = requireText(storagePath, "文档存储路径");
        createdAt = requireNotNull(createdAt, "文档创建时间");
    }

    public static LibraryDocument of(ProjectId projectId,
                                     LibraryType libraryType,
                                     TaskId sourceTaskId,
                                     String filename,
                                     String contentType,
                                     long sizeBytes,
                                     String contentHash,
                                     String storagePath) {
        return new LibraryDocument(
                null, projectId, libraryType, sourceTaskId, filename, contentType,
                sizeBytes, contentHash, storagePath, Instant.now());
    }

    /** 兼容旧调用：未显式指定分类的文件都进入通用文件库。 */
    public static LibraryDocument of(ProjectId projectId,
                                     TaskId sourceTaskId,
                                     String filename,
                                     String contentType,
                                     long sizeBytes,
                                     String contentHash,
                                     String storagePath) {
        return of(projectId, LibraryType.GENERAL, sourceTaskId, filename, contentType,
                sizeBytes, contentHash, storagePath);
    }

    public LibraryDocument withIdentity(LibraryDocumentId assigned) {
        if (id != null) {
            throw new DomainException("文件库文档已有 ID，不能重复分配：" + id);
        }
        return new LibraryDocument(
                requireNotNull(assigned, "文件库文档 ID"), projectId, libraryType, sourceTaskId,
                filename, contentType, sizeBytes, contentHash, storagePath, createdAt);
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value.trim();
    }
}
