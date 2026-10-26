package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.CodeStandardVersion;

import java.time.Instant;

/** 开发规范版本对外契约。 */
public record CodeStandardVersionResponse(
        long id,
        long standardId,
        int versionNo,
        String filename,
        String contentType,
        long sizeBytes,
        String contentHash,
        String contentUrl,
        Instant createdAt) {

    public static CodeStandardVersionResponse from(CodeStandardVersion version) {
        return new CodeStandardVersionResponse(
                version.id().value(),
                version.standardId().value(),
                version.versionNo(),
                version.filename(),
                version.contentType(),
                version.sizeBytes(),
                version.contentHash(),
                "/api/code-standard-versions/" + version.id().value() + "/content",
                version.createdAt());
    }
}
