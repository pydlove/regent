package com.pydlove.regent.plugin.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 可复用能力包聚合。
 *
 * <p>插件内容用于给执行器提供已经写好的代码、协议说明或接入片段。凭据不应写入插件，
 * 凭据仍由环境信息在运行时注入。
 */
public class Plugin {

    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 1_024;
    private static final int MAX_TAGS = 8;
    private static final int MAX_TAG_LENGTH = 32;

    private PluginId id;
    private final String builtinKey;
    private String name;
    private String description;
    private List<String> tags;
    private boolean enabled;
    private PluginVersion currentVersion;
    private final Instant createdAt;
    private Instant updatedAt;

    private Plugin(PluginId id,
                   String builtinKey,
                   String name,
                   String description,
                   List<String> tags,
                   boolean enabled,
                   PluginVersion currentVersion,
                   Instant createdAt,
                   Instant updatedAt) {
        this.id = id;
        this.builtinKey = builtinKey == null ? null : requireBuiltinKey(builtinKey);
        this.name = requireName(name);
        this.description = optionalText(description, MAX_DESCRIPTION_LENGTH, "插件说明");
        this.tags = normalizeTags(tags);
        this.enabled = enabled;
        this.currentVersion = requireNotNull(currentVersion, "插件版本");
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
    }

    public static Plugin create(String name,
                                String description,
                                List<String> tags,
                                String filename,
                                String content,
                                boolean enabled) {
        Instant now = now();
        return new Plugin(
                null,
                null,
                name,
                description,
                tags,
                enabled,
                PluginVersion.initial(filename, content),
                now,
                now);
    }

    public static Plugin createBuiltin(String builtinKey,
                                       String name,
                                       String description,
                                       List<String> tags,
                                       String filename,
                                       String content,
                                       boolean enabled) {
        Instant now = now();
        return new Plugin(
                null,
                builtinKey,
                name,
                description,
                tags,
                enabled,
                PluginVersion.initial(filename, content),
                now,
                now);
    }

    public static Plugin restore(PluginId id,
                                 String builtinKey,
                                 String name,
                                 String description,
                                 List<String> tags,
                                 boolean enabled,
                                 PluginVersion currentVersion,
                                 Instant createdAt,
                                 Instant updatedAt) {
        return new Plugin(
                requireNotNull(id, "插件 ID"),
                builtinKey,
                name,
                description,
                tags,
                enabled,
                currentVersion,
                createdAt,
                updatedAt);
    }

    /** 每次保存内容都生成新版本；历史绑定继续使用旧版本。 */
    public void update(String name,
                       String description,
                       List<String> tags,
                       String filename,
                       String content,
                       boolean enabled) {
        requireManualEditable();
        this.name = requireName(name);
        this.description = optionalText(description, MAX_DESCRIPTION_LENGTH, "插件说明");
        this.tags = normalizeTags(tags);
        this.enabled = enabled;
        this.currentVersion = PluginVersion.next(currentVersion, filename, content);
        this.updatedAt = now();
    }

    /**
     * 将内置插件同步到发行包中的最新定义。
     *
     * <p>只更新内容和元数据，保留用户设置的启停状态；内容与文件名都未变化时只更新元数据，
     * 不生成无意义的新版本。返回是否有字段发生变化，便于启动同步统计。
     */
    public boolean synchronizeBuiltin(String name,
                                      String description,
                                      List<String> tags,
                                      String filename,
                                      String content) {
        if (!isBuiltin()) {
            throw new DomainException("只有内置插件可以执行发行包同步：" + this.name);
        }
        String nextName = requireName(name);
        String nextDescription = optionalText(description, MAX_DESCRIPTION_LENGTH, "插件说明");
        List<String> nextTags = normalizeTags(tags);
        boolean contentChanged = !currentVersion.filename().equals(filename)
                || !currentVersion.content().equals(content);
        boolean changed = !this.name.equals(nextName)
                || !Objects.equals(this.description, nextDescription)
                || !this.tags.equals(nextTags)
                || contentChanged;
        if (!changed) {
            return false;
        }
        this.name = nextName;
        this.description = nextDescription;
        this.tags = nextTags;
        if (contentChanged) {
            this.currentVersion = PluginVersion.next(currentVersion, filename, content);
        }
        this.updatedAt = now();
        return true;
    }

    public void changeEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        this.updatedAt = now();
    }

    public void assignIdentity(PluginId assigned) {
        if (id != null) {
            throw new DomainException("插件已有 ID，不能重复分配：" + id);
        }
        id = requireNotNull(assigned, "插件 ID");
    }

    public PluginBinding bind(Instant boundAt) {
        if (id == null) {
            throw new DomainException("插件尚未持久化，不能绑定任务");
        }
        if (!enabled) {
            throw new DomainException("插件已停用，不能绑定任务：" + name);
        }
        return new PluginBinding(
                id,
                name,
                currentVersion.revision(),
                currentVersion.filename(),
                currentVersion.contentHash(),
                currentVersion.content(),
                true,
                requireNotNull(boundAt, "绑定时间"));
    }

    private static String requireName(String name) {
        String value = requireText(name, "插件名称").trim();
        if (value.length() > MAX_NAME_LENGTH) {
            throw new DomainException("插件名称超长（上限 " + MAX_NAME_LENGTH + " 字符）：" + value.length());
        }
        return value;
    }

    private static String requireBuiltinKey(String builtinKey) {
        String value = requireText(builtinKey, "内置插件标识").trim();
        if (value.length() > 64 || !value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new DomainException("内置插件标识必须是 1-64 位小写字母、数字或连字符：" + value);
        }
        return value;
    }

    private void requireManualEditable() {
        if (isBuiltin()) {
            throw new DomainException("内置插件随发行包更新，不能手工编辑：" + name);
        }
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
            String value = tag.trim();
            if (value.length() > MAX_TAG_LENGTH) {
                throw new DomainException("单个插件标签超长（上限 " + MAX_TAG_LENGTH + " 字符）：" + value);
            }
            if (value.contains(",") || value.contains("\n") || value.contains("\r")) {
                throw new DomainException("插件标签不能包含逗号或换行：" + value);
            }
            normalized.add(value);
        }
        if (normalized.size() > MAX_TAGS) {
            throw new DomainException("插件标签最多 " + MAX_TAGS + " 个");
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    private static String optionalText(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new DomainException(field + "超长（上限 " + max + " 字符）：" + normalized.length());
        }
        return normalized;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    public PluginId getId() {
        return id;
    }

    public String getBuiltinKey() {
        return builtinKey;
    }

    public boolean isBuiltin() {
        return builtinKey != null;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public PluginVersion getCurrentVersion() {
        return currentVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
