package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.DecisionLevel;

/**
 * 任务列表的查询条件。
 *
 * <p>参数在构造时就地规范化（页码小于 1 拉回 1、每页条数夹在 1–200），而不是抛异常：
 * 这些值来自 URL 查询串，被手工改过是常态，为一个可修正的页码返回 400 只会让人困惑。
 * 类型错误（比如状态或等级写成非法值）仍然拒绝 —— 那说明调用方理解错了契约，不该被静默忽略。
 *
 * <p>{@code level} 过滤的是任务最新一条决策的仲裁后等级；尚无决策的任务按 L0 处理，
 * 与列表展示口径保持一致。
 */
public record TaskQuery(TaskStatus status,
                        DecisionLevel level,
                        ProjectId projectId,
                        TaskId taskId,
                        String keyword,
                        int page,
                        int size,
                        UserId ownerId) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public TaskQuery {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    /** 兼容内部与测试的无归属查询。接口侧必须使用带 ownerId 的工厂方法。 */
    public TaskQuery(TaskStatus status,
                     DecisionLevel level,
                     ProjectId projectId,
                     TaskId taskId,
                     String keyword,
                     int page,
                     int size) {
        this(status, level, projectId, taskId, keyword, page, size, null);
    }

    /** 兼容不按任务号筛选的调用方。 */
    public TaskQuery(TaskStatus status,
                     DecisionLevel level,
                     ProjectId projectId,
                     String keyword,
                     int page,
                     int size) {
        this(status, level, projectId, null, keyword, page, size);
    }

    /** 供接口层直接使用：把 URL 参数一次性换成受校验的查询条件。 */
    public static TaskQuery of(String status,
                               String level,
                               Long projectId,
                               Long taskId,
                               String keyword,
                               Integer page,
                               Integer size) {
        return of(status, level, projectId, taskId, keyword, page, size, null);
    }

    public static TaskQuery of(String status,
                               String level,
                               Long projectId,
                               Long taskId,
                               String keyword,
                               Integer page,
                               Integer size,
                               UserId ownerId) {
        return new TaskQuery(
                (status == null || status.isBlank()) ? null : TaskStatus.parse(status),
                (level == null || level.isBlank()) ? null : DecisionLevel.parse(level),
                // 非法 ID（0、负数）走 ProjectId 的守卫直接报错：那说明调用方理解错了契约
                projectId == null ? null : ProjectId.of(projectId),
                taskId == null ? null : TaskId.of(taskId),
                keyword,
                page == null ? 1 : page,
                size == null ? DEFAULT_SIZE : size,
                ownerId);
    }

    /** 兼容不按任务号筛选的调用方。 */
    public static TaskQuery of(String status,
                               String level,
                               Long projectId,
                               String keyword,
                               Integer page,
                               Integer size) {
        return of(status, level, projectId, null, keyword, page, size);
    }

    /** 不带筛选的查询，测试与小工具用。 */
    public static TaskQuery firstPage() {
        return new TaskQuery(null, null, null, null, null, 1, DEFAULT_SIZE, null);
    }

    public boolean hasStatusFilter() {
        return status != null;
    }

    public boolean hasLevelFilter() {
        return level != null;
    }

    public boolean hasProjectFilter() {
        return projectId != null;
    }

    public boolean hasTaskFilter() {
        return taskId != null;
    }

    public boolean hasKeywordFilter() {
        return keyword != null;
    }

    public int offset() {
        return (page - 1) * size;
    }
}
