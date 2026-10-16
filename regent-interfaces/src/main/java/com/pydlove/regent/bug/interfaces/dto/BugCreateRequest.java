package com.pydlove.regent.bug.interfaces.dto;

/** 人工录入缺陷单的请求体。 */
public record BugCreateRequest(
        Long projectId,
        String title,
        String severity,
        String reproduction,
        String filePath,
        String detail) {
}
