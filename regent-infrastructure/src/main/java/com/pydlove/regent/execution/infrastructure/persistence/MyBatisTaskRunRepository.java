package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 执行记录仓储的 MyBatis 实现。 */
@Repository
public class MyBatisTaskRunRepository implements TaskRunRepository {

    private final TaskRunMapper taskRunMapper;

    public MyBatisTaskRunRepository(TaskRunMapper taskRunMapper) {
        this.taskRunMapper = taskRunMapper;
    }

    @Override
    public Optional<TaskRun> findById(TaskRunId id) {
        return Optional.ofNullable(taskRunMapper.selectById(id.value()))
                .map(TaskRunDataMapper::toDomain);
    }

    @Override
    public Optional<TaskRun> findLatestByTask(TaskId taskId) {
        return Optional.ofNullable(taskRunMapper.selectLatestByTask(taskId.value()))
                .map(TaskRunDataMapper::toDomain);
    }

    @Override
    public List<TaskRun> listByTask(TaskId taskId) {
        return taskRunMapper.selectByTask(taskId.value()).stream()
                .map(TaskRunDataMapper::toDomain)
                .toList();
    }

    @Override
    public void save(TaskRun run) {
        TaskRunData data = TaskRunDataMapper.toData(run);
        if (run.getId() == null) {
            taskRunMapper.insert(data);
            run.assignIdentity(TaskRunId.of(data.getId()));
            return;
        }
        int affected = taskRunMapper.update(data);
        if (affected == 0) {
            throw new DomainException("执行记录未更新，可能已被清理：" + run.getId());
        }
    }
}
