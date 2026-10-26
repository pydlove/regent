package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.query.CodeStandardView;
import com.pydlove.regent.execution.domain.model.CodeStandard;
import com.pydlove.regent.execution.domain.model.CodeStandardVersion;

import java.time.Instant;

/** 项目开发规范条目与当前版本。 */
public record CodeStandardResponse(
        long id,
        long projectId,
        String name,
        String description,
        String scope,
        boolean enabled,
        boolean defaultApply,
        int sortOrder,
        boolean archived,
        Long sourceProjectId,
        Long sourceStandardId,
        Integer sourceVersionNo,
        int currentVersion,
        String filename,
        long sizeBytes,
        String contentHash,
        String contentUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static CodeStandardResponse from(CodeStandardView view) {
        CodeStandard standard = view.standard();
        CodeStandardVersion version = view.currentVersion();
        return new CodeStandardResponse(
                standard.id().value(),
                standard.projectId().value(),
                standard.name(),
                standard.description(),
                standard.scope().name(),
                standard.enabled(),
                standard.defaultApply(),
                standard.sortOrder(),
                standard.archived(),
                standard.sourceProjectId() == null ? null : standard.sourceProjectId().value(),
                standard.sourceStandardId() == null ? null : standard.sourceStandardId().value(),
                standard.sourceVersionNo(),
                version == null ? 0 : version.versionNo(),
                version == null ? null : version.filename(),
                version == null ? 0 : version.sizeBytes(),
                version == null ? null : version.contentHash(),
                version == null ? null : "/api/code-standard-versions/" + version.id().value() + "/content",
                standard.createdAt(),
                standard.updatedAt());
    }
}
