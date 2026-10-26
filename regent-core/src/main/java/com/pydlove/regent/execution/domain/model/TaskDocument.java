package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/** 任务文档。不可变事实，可引用文件库中的同一份内容，也可来自本次任务的一次性上传。 */
public record TaskDocument(
        TaskDocumentId id,
        TaskId taskId,
        LibraryDocumentId libraryDocumentId,
        String filename,
        String contentType,
        long sizeBytes,
        String storagePath,
        Instant createdAt) {

    public TaskDocument {
        taskId = requireNotNull(taskId, "任务文档所属任务");
        filename = requireText(filename, "文档文件名");
        contentType = requireText(contentType, "文档类型");
        if (contentType.startsWith("image/")) {
            throw new DomainException("任务文档不能使用图片类型：" + contentType);
        }
        if (sizeBytes <= 0) {
            throw new DomainException("文档大小必须为正：" + sizeBytes);
        }
        storagePath = requireText(storagePath, "文档存储路径");
        createdAt = requireNotNull(createdAt, "文档创建时间");
    }

    public static TaskDocument of(TaskId taskId,
                                  LibraryDocumentId libraryDocumentId,
                                  String filename,
                                  String contentType,
                                  long sizeBytes,
                                  String storagePath) {
        return new TaskDocument(
                null, taskId, libraryDocumentId, filename, contentType,
                sizeBytes, storagePath, Instant.now());
    }

    public TaskDocument withIdentity(TaskDocumentId assigned) {
        if (id != null) {
            throw new DomainException("任务文档已有 ID，不能重复分配：" + id);
        }
        return new TaskDocument(
                requireNotNull(assigned, "任务文档 ID"), taskId, libraryDocumentId,
                filename, contentType, sizeBytes, storagePath, createdAt);
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
