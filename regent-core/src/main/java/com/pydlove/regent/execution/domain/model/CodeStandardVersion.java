package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 开发规范的不可变文件版本。 */
public record CodeStandardVersion(
        CodeStandardVersionId id,
        CodeStandardId standardId,
        ProjectId projectId,
        int versionNo,
        LibraryDocumentId libraryDocumentId,
        String filename,
        String contentType,
        long sizeBytes,
        String contentHash,
        String storagePath,
        Instant createdAt) {

    public CodeStandardVersion {
        standardId = requireNotNull(standardId, "开发规范 ID");
        projectId = requireNotNull(projectId, "开发规范所属项目");
        if (versionNo <= 0) {
            throw new DomainException("开发规范版本号必须为正");
        }
        libraryDocumentId = requireNotNull(libraryDocumentId, "开发规范文件库文档 ID");
        filename = requireText(filename, "开发规范文件名");
        contentType = requireText(contentType, "开发规范文件类型");
        if (sizeBytes <= 0) {
            throw new DomainException("开发规范文件大小必须为正");
        }
        contentHash = requireText(contentHash, "开发规范文件摘要");
        if (contentHash.length() != 64) {
            throw new DomainException("开发规范文件摘要必须是 SHA-256");
        }
        storagePath = requireText(storagePath, "开发规范存储路径");
        createdAt = requireNotNull(createdAt, "开发规范版本创建时间");
    }

    public static CodeStandardVersion of(CodeStandardId standardId,
                                         ProjectId projectId,
                                         int versionNo,
                                         LibraryDocument document) {
        return new CodeStandardVersion(null, standardId, projectId, versionNo,
                document.id(), document.filename(), document.contentType(), document.sizeBytes(),
                document.contentHash(), document.storagePath(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    public CodeStandardVersion withIdentity(CodeStandardVersionId assigned) {
        if (id != null) {
            throw new DomainException("开发规范版本已有 ID，不能重复分配：" + id);
        }
        return new CodeStandardVersion(requireNotNull(assigned, "开发规范版本 ID"), standardId,
                projectId, versionNo, libraryDocumentId, filename, contentType, sizeBytes,
                contentHash, storagePath, createdAt);
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
