package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 人工录入任务的草稿。
 *
 * <p>草稿不是任务：它没有状态机，也永远不会进入派发查询。它允许项目和指令暂时为空，
 * 因为自动保存必须能接住用户刚打开弹框、还没写完的那一刻。
 */
public class TaskDraft {

    public static final int MAX_PLUGINS = 8;

    private TaskDraftId id;
    private final UserId userId;
    private ProjectId projectId;
    private String prompt;
    private int priority;
    private List<PluginId> pluginIds;
    private final Instant createdAt;
    private Instant updatedAt;

    private TaskDraft(TaskDraftId id,
                      UserId userId,
                      ProjectId projectId,
                      String prompt,
                      int priority,
                      List<PluginId> pluginIds,
                      Instant createdAt,
                      Instant updatedAt) {
        this.id = id;
        this.userId = requireNotNull(userId, "草稿所属用户");
        this.projectId = projectId;
        this.prompt = prompt == null ? "" : prompt;
        this.priority = priority;
        this.pluginIds = normalizePluginIds(pluginIds);
        this.createdAt = requireNotNull(createdAt, "创建时间");
        this.updatedAt = requireNotNull(updatedAt, "更新时间");
    }

    public static TaskDraft create(UserId userId) {
        Instant now = now();
        return new TaskDraft(null, userId, null, "", 0, List.of(), now, now);
    }

    public static TaskDraft restore(TaskDraftSnapshot snapshot) {
        return new TaskDraft(
                requireNotNull(snapshot.id(), "草稿 ID"),
                snapshot.userId(),
                snapshot.projectId(),
                snapshot.prompt(),
                snapshot.priority(),
                snapshot.pluginIds(),
                snapshot.createdAt(),
                snapshot.updatedAt());
    }

    public void update(ProjectId projectId,
                       String prompt,
                       int priority,
                       List<PluginId> pluginIds) {
        this.projectId = projectId;
        this.prompt = prompt == null ? "" : prompt;
        this.priority = priority;
        this.pluginIds = normalizePluginIds(pluginIds);
        this.updatedAt = now();
    }

    public void assignIdentity(TaskDraftId assigned) {
        if (this.id != null) {
            throw new DomainException("任务草稿已有 ID，不能重复分配：" + this.id);
        }
        this.id = requireNotNull(assigned, "草稿 ID");
    }

    private static List<PluginId> normalizePluginIds(List<PluginId> pluginIds) {
        if (pluginIds == null || pluginIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<PluginId> unique = new LinkedHashSet<>(pluginIds);
        if (unique.contains(null)) {
            throw new DomainException("插件 ID 不能为空");
        }
        if (unique.size() > MAX_PLUGINS) {
            throw new DomainException("一个任务最多选择 " + MAX_PLUGINS + " 个插件");
        }
        return List.copyOf(unique);
    }

    private static <T> T requireNotNull(T value, String name) {
        if (value == null) {
            throw new DomainException(name + "不能为空");
        }
        return value;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public TaskDraftId getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public Optional<ProjectId> getProjectId() {
        return Optional.ofNullable(projectId);
    }

    public String getPrompt() {
        return prompt;
    }

    public int getPriority() {
        return priority;
    }

    public List<PluginId> getPluginIds() {
        return pluginIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
