package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.TaskCodeStandard;

import java.time.Instant;

/** 任务实际使用的开发规范版本快照。 */
public record TaskCodeStandardResponse(
        long id,
        long taskId,
        long standardId,
        long standardVersionId,
        String name,
        int versionNo,
        String filename,
        String contentType,
        long sizeBytes,
        String contentHash,
        String contentUrl,
        Long sourceProjectId,
        Instant createdAt) {

    public static TaskCodeStandardResponse from(TaskCodeStandard standard) {
        return new TaskCodeStandardResponse(
                standard.id().value(),
                standard.taskId().value(),
                standard.standardId().value(),
                standard.standardVersionId().value(),
                standard.standardName(),
                standard.versionNo(),
                standard.filename(),
                standard.contentType(),
                standard.sizeBytes(),
                standard.contentHash(),
                "/api/task-code-standards/" + standard.id().value() + "/content",
                standard.sourceProjectId() == null ? null : standard.sourceProjectId().value(),
                standard.createdAt());
    }
}
