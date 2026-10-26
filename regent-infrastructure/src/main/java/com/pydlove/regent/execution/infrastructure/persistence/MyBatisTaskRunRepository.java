package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    public Optional<TaskRun> findByIdOwned(TaskRunId id, UserId ownerId) {
        return Optional.ofNullable(taskRunMapper.selectByIdOwned(id.value(), ownerId.value()))
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
    public List<TaskRun> findRunningInternal() {
        return taskRunMapper.selectRunningInternal(TaskRun.EXECUTOR_INTERNAL).stream()
                .map(TaskRunDataMapper::toDomain)
                .toList();
    }

    @Override
    public List<TaskRun> findExpiredLeases(Instant now) {
        return taskRunMapper.selectExpiredLeases(TaskRun.EXECUTOR_AGENT, toDb(now)).stream()
                .map(TaskRunDataMapper::toDomain)
                .toList();
    }

    @Override
    public List<TaskRun> findActiveLeases(Instant now) {
        return taskRunMapper.selectActiveLeases(TaskRun.EXECUTOR_AGENT, toDb(now)).stream()
                .map(TaskRunDataMapper::toDomain)
                .toList();
    }

    @Override
    public List<TaskRun> findRunningByDevice(String deviceId) {
        return taskRunMapper.selectRunningByDevice(deviceId).stream()
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

    @Override
    public void renewLease(TaskRunId runId,
                           String deviceId,
                           Instant leaseExpiresAt,
                           Instant lastHeartbeatAt) {
        if (taskRunMapper.updateLease(runId.value(), deviceId,
                toDb(leaseExpiresAt), toDb(lastHeartbeatAt)) == 0) {
            throw new DomainException("续租失败，执行记录不存在：" + runId);
        }
    }

    @Override
    public void touchLastEvent(TaskRunId runId, Instant occurredAt) {
        if (occurredAt != null) {
            taskRunMapper.updateLastEvent(runId.value(), toDb(occurredAt));
        }
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
