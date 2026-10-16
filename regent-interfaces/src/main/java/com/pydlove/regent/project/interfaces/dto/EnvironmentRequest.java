package com.pydlove.regent.project.interfaces.dto;

import java.util.List;

/** 新建或整体更新一条环境信息。 */
public record EnvironmentRequest(
        String scope,
        Long projectId,
        String key,
        String value,
        List<String> tags,
        String description) {
}
