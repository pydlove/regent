package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 研发流程模板中的一个阶段或门禁。 */
public record WorkflowTemplateStage(
        String code,
        String name,
        int order,
        String scope,
        String approverRole,
        String description) {

    public WorkflowTemplateStage {
        code = code == null ? null : code.trim();
        name = name == null ? null : name.trim();
        scope = scope == null ? null : scope.trim().toUpperCase();
        approverRole = approverRole == null || approverRole.isBlank()
                ? null
                : approverRole.trim().toUpperCase();
        description = description == null ? null : description.trim();
        if (code == null || code.isBlank()) {
            throw new DomainException("流程阶段编码不能为空");
        }
        if (code.length() > 48) {
            throw new DomainException("流程阶段编码不能超过 48 个字符");
        }
        if (name == null || name.isBlank()) {
            throw new DomainException("流程阶段名称不能为空");
        }
        if (name.length() > 80) {
            throw new DomainException("流程阶段名称不能超过 80 个字符");
        }
        if (order <= 0) {
            throw new DomainException("流程阶段顺序必须大于 0");
        }
        if (!"VERSION".equals(scope) && !"REQUIREMENT".equals(scope)) {
            throw new DomainException("流程阶段范围只能是 VERSION 或 REQUIREMENT");
        }
        if (approverRole != null) {
            try {
                ProjectMemberRole.valueOf(approverRole);
            } catch (IllegalArgumentException exception) {
                throw new DomainException("未知的流程审批角色：" + approverRole);
            }
        }
        if (description != null && description.length() > 500) {
            throw new DomainException("流程阶段说明不能超过 500 个字符");
        }
    }
}
