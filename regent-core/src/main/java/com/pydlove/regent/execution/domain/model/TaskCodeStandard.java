package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 任务创建时固定的开发规范版本快照。 */
public record TaskCodeStandard(
        TaskCodeStandardId id,
        TaskId taskId,
        CodeStandardId standardId,
        CodeStandardVersionId standardVersionId,
        String standardName,
        int versionNo,
        String filename,
        String contentType,
        long sizeBytes,
        String contentHash,
        String storagePath,
        ProjectId sourceProjectId,
        Instant createdAt) {

    public TaskCodeStandard {
        taskId = requireNotNull(taskId, "任务 ID");
        standardId = requireNotNull(standardId, "开发规范 ID");
        standardVersionId = requireNotNull(standardVersionId, "开发规范版本 ID");
        standardName = requireText(standardName, "开发规范名称");
        if (versionNo <= 0) {
            throw new DomainException("开发规范版本号必须为正");
        }
        filename = requireText(filename, "开发规范文件名");
        contentType = requireText(contentType, "开发规范文件类型");
        if (sizeBytes <= 0) {
            throw new DomainException("开发规范文件大小必须为正");
        }
        contentHash = requireText(contentHash, "开发规范文件摘要");
        storagePath = requireText(storagePath, "开发规范存储路径");
        createdAt = requireNotNull(createdAt, "任务开发规范快照创建时间");
    }

    public static TaskCodeStandard of(TaskId taskId, CodeStandard standard,
                                      CodeStandardVersion version) {
        return new TaskCodeStandard(null, taskId, standard.id(), version.id(), standard.name(),
                version.versionNo(), version.filename(), version.contentType(), version.sizeBytes(),
                version.contentHash(), version.storagePath(), standard.sourceProjectId(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    public TaskCodeStandard withIdentity(TaskCodeStandardId assigned) {
        if (id != null) {
            throw new DomainException("任务开发规范快照已有 ID，不能重复分配：" + id);
        }
        return new TaskCodeStandard(requireNotNull(assigned, "任务开发规范快照 ID"), taskId,
                standardId, standardVersionId, standardName, versionNo, filename, contentType,
                sizeBytes, contentHash, storagePath, sourceProjectId, createdAt);
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
