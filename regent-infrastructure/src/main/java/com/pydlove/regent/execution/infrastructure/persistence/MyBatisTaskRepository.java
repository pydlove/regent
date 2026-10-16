package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 任务仓储的 MyBatis 实现。
 *
 * <p>它是「领域」与「表结构」之间唯一的接缝：领域层看到的是 {@code Task} 聚合，
 * 数据库看到的是 {@code task} 表，两边形状不同，差异全部由 {@link TaskDataMapper} 吸收。
 */
@Repository
public class MyBatisTaskRepository implements TaskRepository {

    private final TaskMapper taskMapper;

    public MyBatisTaskRepository(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public Optional<Task> findById(TaskId id) {
        return Optional.ofNullable(taskMapper.selectById(id.value()))
                .map(TaskDataMapper::toDomain);
    }

    @Override
    public List<Task> findDispatchable(int limit) {
        return taskMapper.selectDispatchable(limit).stream()
                .map(TaskDataMapper::toDomain)
                .toList();
    }

    @Override
    public void save(Task task) {
        TaskData data = TaskDataMapper.toData(task);
        if (task.getId() == null) {
            taskMapper.insert(data);
            // 主键由数据库生成，insert 后回填 —— 聚合自己不做 ID 分配，
            // 否则「谁生成 ID」这件事就变成了两份实现。
            task.assignIdentity(TaskId.of(data.getId()));
            return;
        }
        int affected = taskMapper.update(data);
        if (affected == 0) {
            throw new DomainException("任务未更新，可能已被删除：" + task.getId());
        }
    }
}
