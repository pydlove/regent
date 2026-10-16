package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskStatus;

import java.util.Map;

/**
 * 任务查询仓储（只读）。
 *
 * <p>与 {@link TaskRepository} 的分工：这里回答「列表要显示什么」，那里回答「任务能做什么」。
 */
public interface TaskQueryRepository {

    TaskListItemPage findPage(TaskQuery query);

    /** 状态分布。缺失的状态不出现在 Map 里，由应用服务补零。 */
    Map<TaskStatus, Long> countGroupByStatus();
}
