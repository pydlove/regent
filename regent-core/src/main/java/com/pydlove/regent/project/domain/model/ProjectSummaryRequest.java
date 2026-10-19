package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Objects;

/** 生成项目资料所需的只读项目上下文。 */
public record ProjectSummaryRequest(
        ProjectId projectId,
        String code,
        String name,
        String path,
        String techStack,
        String verifyMethod) {

    public ProjectSummaryRequest {
        Objects.requireNonNull(projectId, "项目 ID 不能为空");
        if (code == null || code.isBlank()) {
            throw new DomainException("项目标识不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new DomainException("项目名称不能为空");
        }
        if (path == null || path.isBlank()) {
            throw new DomainException("项目路径不能为空");
        }
    }
}
