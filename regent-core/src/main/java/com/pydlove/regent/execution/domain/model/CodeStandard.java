package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 项目开发规范条目；内容版本另存为不可变快照。 */
public record CodeStandard(
        CodeStandardId id,
        ProjectId projectId,
        String name,
        String description,
        CodeStandardScope scope,
        boolean enabled,
        boolean defaultApply,
        int sortOrder,
        boolean archived,
        ProjectId sourceProjectId,
        CodeStandardId sourceStandardId,
        CodeStandardVersionId sourceVersionId,
        Integer sourceVersionNo,
        Instant createdAt,
        Instant updatedAt) {

    public CodeStandard {
        projectId = requireNotNull(projectId, "开发规范所属项目");
        name = requireText(name, "规范名称", 160);
        description = description == null || description.isBlank()
                ? null : requireText(description, "规范说明", 512);
        scope = scope == null ? CodeStandardScope.GENERAL : scope;
        defaultApply = enabled && defaultApply;
        createdAt = requireNotNull(createdAt, "开发规范创建时间");
        updatedAt = requireNotNull(updatedAt, "开发规范更新时间");
        if (archived && enabled) {
            throw new DomainException("已归档的开发规范不能同时处于启用状态");
        }
        if (sourceVersionNo != null && sourceVersionNo <= 0) {
            throw new DomainException("来源规范版本号必须为正");
        }
    }

    public static CodeStandard create(ProjectId projectId,
                                      String name,
                                      String description,
                                      CodeStandardScope scope,
                                      boolean enabled,
                                      boolean defaultApply,
                                      int sortOrder) {
        Instant now = now();
        return new CodeStandard(null, projectId, name, description, scope, enabled, defaultApply,
                sortOrder, false, null, null, null, null, now, now);
    }

    public static CodeStandard copied(ProjectId projectId,
                                      String name,
                                      String description,
                                      CodeStandardScope scope,
                                      boolean enabled,
                                      boolean defaultApply,
                                      int sortOrder,
                                      ProjectId sourceProjectId,
                                      CodeStandardId sourceStandardId,
                                      CodeStandardVersionId sourceVersionId,
                                      int sourceVersionNo) {
        Instant now = now();
        return new CodeStandard(null, projectId, name, description, scope, enabled, defaultApply,
                sortOrder, false, sourceProjectId, sourceStandardId, sourceVersionId,
                sourceVersionNo, now, now);
    }

    public CodeStandard update(String name,
                               String description,
                               CodeStandardScope scope,
                               boolean enabled,
                               boolean defaultApply,
                               int sortOrder) {
        requireActive();
        return new CodeStandard(id, projectId, name, description, scope, enabled,
                enabled && defaultApply,
                sortOrder, archived, sourceProjectId, sourceStandardId, sourceVersionId,
                sourceVersionNo, createdAt, now());
    }

    public CodeStandard enable() {
        requireNotArchived();
        return new CodeStandard(id, projectId, name, description, scope, true, defaultApply,
                sortOrder, archived, sourceProjectId, sourceStandardId, sourceVersionId,
                sourceVersionNo, createdAt, now());
    }

    public CodeStandard disable() {
        return new CodeStandard(id, projectId, name, description, scope, false, false,
                sortOrder, archived, sourceProjectId, sourceStandardId, sourceVersionId,
                sourceVersionNo, createdAt, now());
    }

    public CodeStandard archive() {
        return new CodeStandard(id, projectId, name, description, scope, false, false,
                sortOrder, true, sourceProjectId, sourceStandardId, sourceVersionId,
                sourceVersionNo, createdAt, now());
    }

    public CodeStandard withIdentity(CodeStandardId assigned) {
        if (id != null) {
            throw new DomainException("开发规范已有 ID，不能重复分配：" + id);
        }
        return new CodeStandard(requireNotNull(assigned, "开发规范 ID"), projectId, name,
                description, scope, enabled, defaultApply, sortOrder, archived,
                sourceProjectId, sourceStandardId, sourceVersionId, sourceVersionNo,
                createdAt, updatedAt);
    }

    public void requireUsable() {
        if (archived || !enabled) {
            throw new DomainException("开发规范未启用：" + name);
        }
    }

    private void requireActive() {
        if (archived) {
            throw new DomainException("已归档的开发规范不能编辑：" + name);
        }
    }

    private void requireNotArchived() {
        if (archived) {
            throw new DomainException("已归档的开发规范不能启用：" + name);
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainException(field + "不能超过 " + maxLength + " 字");
        }
        return normalized;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
