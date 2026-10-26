package com.pydlove.regent.requirement.interfaces.dto;

/** 需求草稿写入请求。 */
public record RequirementSaveRequest(
        Long projectId,
        Long initiativeId,
        Long versionId,
        String source,
        String title,
        String rawText,
        String acceptanceText,
        String clarificationText) {
}
