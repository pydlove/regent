package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskListItemPage;
import com.pydlove.regent.execution.domain.repository.TaskQuery;
import com.pydlove.regent.execution.domain.repository.TaskQueryRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.EnumMap;
import java.util.Map;

/**
 * 任务读侧用例。刻意只做编排、不做判断：筛选与分页下推给仓储的 SQL，
 * 聚合内部的状态判断留在领域方法里。
 */
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final TaskQueryRepository taskQueryRepository;

    public TaskQueryService(TaskRepository taskRepository, TaskQueryRepository taskQueryRepository) {
        this.taskRepository = taskRepository;
        this.taskQueryRepository = taskQueryRepository;
    }

    public TaskListItemPage list(TaskQuery query) {
        return taskQueryRepository.findPage(query);
    }

    /** 详情取聚合：状态守卫、可执行动作都以聚合为准，不另开一份读模型。 */
    public Task get(TaskId id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + id));
    }

    /** 状态分布。缺失的状态补 0，让调用方拿到的键集合固定是六个。 */
    public Map<TaskStatus, Long> statusCounts() {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        counts.putAll(taskQueryRepository.countGroupByStatus());
        return counts;
    }
}
