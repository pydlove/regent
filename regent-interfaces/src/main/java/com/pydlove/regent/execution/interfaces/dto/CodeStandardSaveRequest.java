package com.pydlove.regent.execution.interfaces.dto;

/** 新建或更新开发规范元数据。 */
public record CodeStandardSaveRequest(
        Long projectId,
        String name,
        String description,
        String scope,
        Boolean enabled,
        Boolean defaultApply,
        Integer sortOrder) {
}
