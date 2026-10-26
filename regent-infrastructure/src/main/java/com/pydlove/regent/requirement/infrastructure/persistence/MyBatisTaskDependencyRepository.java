package com.pydlove.regent.requirement.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.requirement.domain.repository.TaskDependencyRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** 任务依赖仓储实现。 */
@Repository
public class MyBatisTaskDependencyRepository implements TaskDependencyRepository {

    private final TaskDependencyMapper mapper;

    public MyBatisTaskDependencyRepository(TaskDependencyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void replace(TaskId taskId, Collection<TaskId> dependsOnTaskIds) {
        mapper.deleteByTask(taskId.value());
        List<TaskId> values = dependsOnTaskIds == null ? List.of() : List.copyOf(dependsOnTaskIds);
        if (!values.isEmpty()) {
            mapper.insertDependencies(taskId.value(), values.stream().map(TaskId::value).toList());
        }
    }

    @Override
    public List<TaskId> findDependencies(TaskId taskId) {
        return mapper.selectDependencies(taskId.value()).stream().map(TaskId::of).toList();
    }
}
