package com.pydlove.regent.project.interfaces.dto;

import java.util.List;

/** 新建或整体更新一条环境信息。环境键由平台自动生成，客户端不再提交。 */
public record EnvironmentRequest(
        String scope,
        Long projectId,
        String value,
        List<String> tags,
        String description,
        Boolean secret) {
}
