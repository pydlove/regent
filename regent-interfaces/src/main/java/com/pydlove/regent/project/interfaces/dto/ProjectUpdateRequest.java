package com.pydlove.regent.project.interfaces.dto;

/**
 * 编辑项目的请求体。项目标识不在请求里，创建后不可修改。
 *
 * <p>{@code enabled} 用包装类型，缺字段时明确拒绝，不能把漏传静默解释成暂停项目。
 */
public record ProjectUpdateRequest(
        String name,
        String path,
        String defaultDecisionLevel,
        Boolean enabled) {
}
