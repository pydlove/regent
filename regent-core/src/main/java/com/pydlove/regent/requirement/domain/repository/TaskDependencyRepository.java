package com.pydlove.regent.requirement.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskId;

import java.util.Collection;
import java.util.List;

/** 需求拆解任务之间的依赖关系。 */
public interface TaskDependencyRepository {

    void replace(TaskId taskId, Collection<TaskId> dependsOnTaskIds);

    List<TaskId> findDependencies(TaskId taskId);
}
