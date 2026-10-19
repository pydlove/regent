package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 一条可供任务执行器使用的环境信息。
 *
 * <p>全局条目不属于任何项目；项目条目必须明确归属。执行时项目级同键条目覆盖全局条目，
 * 让公共默认值可以按项目定向覆盖，而不必在每个任务里重复维护。
 */
public class EnvironmentEntry {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");
    private static final int MAX_VALUE = 65_535;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_TAGS = 8;
    private static final int MAX_TAG_LENGTH = 32;

    private EnvironmentId id;
    private final UserId userId;
    private EnvironmentScope scope;
    private ProjectId projectId;
    private String key;
    private String value;
    private List<String> tags;
    private String description;
    private boolean secret;
    private final Instant createdAt;
    private Instant updatedAt;

    private EnvironmentEntry(EnvironmentId id,
                             UserId userId,
                             EnvironmentScope scope,
                             ProjectId projectId,
                             String key,
                             String value,
                             List<String> tags,
                             String description,
                             boolean secret,
                             Instant createdAt,
                             Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        apply(scope, projectId, key, value, tags, description, secret);
        this.createdAt = requireNotNull(createdAt, "创建时间");
        this.updatedAt = requireNotNull(updatedAt, "更新时间");
    }

    public static EnvironmentEntry create(EnvironmentScope scope,
                                          ProjectId projectId,
                                          String key,
                                          String value,
                                          List<String> tags,
                                          String description) {
        return create(null, scope, projectId, key, value, tags, description, false);
    }

    public static EnvironmentEntry create(EnvironmentScope scope,
                                          ProjectId projectId,
                                          String key,
                                          String value,
                                          List<String> tags,
                                          String description,
                                          boolean secret) {
        return create(null, scope, projectId, key, value, tags, description, secret);
    }

    /** 创建一条归属于指定账号的环境信息。 */
    public static EnvironmentEntry create(UserId userId,
                                          EnvironmentScope scope,
                                          ProjectId projectId,
                                          String key,
                                          String value,
                                          List<String> tags,
                                          String description,
                                          boolean secret) {
        Instant now = Instant.now();
        return new EnvironmentEntry(
                null, userId, scope, projectId, key, value, tags, description, secret, now, now);
    }

    /** 从持久化数据重建，保留原始创建与更新时间。 */
    public static EnvironmentEntry restore(EnvironmentId id,
                                           EnvironmentScope scope,
                                           ProjectId projectId,
                                           String key,
                                           String value,
                                           List<String> tags,
                                           String description,
                                           boolean secret,
                                           Instant createdAt,
                                           Instant updatedAt) {
        return restore(id, null, scope, projectId, key, value, tags, description, secret,
                createdAt, updatedAt);
    }

    public static EnvironmentEntry restore(EnvironmentId id,
                                           UserId userId,
                                           EnvironmentScope scope,
                                           ProjectId projectId,
                                           String key,
                                           String value,
                                           List<String> tags,
                                           String description,
                                           boolean secret,
                                           Instant createdAt,
                                           Instant updatedAt) {
        return new EnvironmentEntry(
                id, userId, scope, projectId, key, value, tags, description, secret, createdAt, updatedAt);
    }

    public void update(EnvironmentScope scope,
                       ProjectId projectId,
                       String key,
                       String value,
                       List<String> tags,
                       String description,
                       boolean secret) {
        apply(scope, projectId, key, value, tags, description, secret);
        this.updatedAt = Instant.now();
    }

    public void assignIdentity(EnvironmentId assigned) {
        if (this.id != null) {
            throw new DomainException("环境条目已有 ID，不能重复分配：" + this.id);
        }
        this.id = requireNotNull(assigned, "环境条目 ID");
    }

    private void apply(EnvironmentScope scope,
                       ProjectId projectId,
                       String key,
                       String value,
                       List<String> tags,
                       String description,
                       boolean secret) {
        this.scope = requireNotNull(scope, "环境归属");
        if (scope == EnvironmentScope.GLOBAL && projectId != null) {
            throw new DomainException("全局环境不能绑定项目");
        }
        if (scope == EnvironmentScope.PROJECT && projectId == null) {
            throw new DomainException("项目环境必须指定项目");
        }
        this.projectId = projectId;
        this.key = requireKey(key);
        this.value = requireValue(value);
        this.tags = normalizeTags(tags);
        this.description = requireName(description);
        this.secret = secret;
    }

    public EnvironmentId getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public EnvironmentScope getScope() {
        return scope;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSecret() {
        return secret;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new DomainException("环境键不能为空");
        }
        String normalized = key.trim();
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new DomainException("环境键只能以字母或下划线开头，且只含字母、数字与下划线："
                    + normalized);
        }
        return normalized;
    }

    private static String requireValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() > MAX_VALUE) {
            throw new DomainException("环境信息超长（上限 " + MAX_VALUE + " 字符）：" + value.length());
        }
        return value;
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.length() > MAX_TAG_LENGTH) {
                throw new DomainException("单个标签超长（上限 " + MAX_TAG_LENGTH + " 字符）：" + trimmed);
            }
            if (trimmed.indexOf(',') >= 0 || trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
                throw new DomainException("标签不能包含逗号或换行：" + trimmed);
            }
            normalized.add(trimmed);
        }
        if (normalized.size() > MAX_TAGS) {
            throw new DomainException("标签最多 " + MAX_TAGS + " 个");
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("请填写环境信息名称");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new DomainException(
                    "环境信息名称超长（上限 " + MAX_NAME_LENGTH + " 字符）：" + trimmed.length());
        }
        return trimmed;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }
}
