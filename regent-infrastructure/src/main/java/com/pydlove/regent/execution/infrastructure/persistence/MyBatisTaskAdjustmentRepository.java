package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.TaskAdjustment;
import com.pydlove.regent.execution.domain.model.TaskAdjustmentId;
import com.pydlove.regent.execution.domain.model.TaskAdjustmentSource;
import com.pydlove.regent.execution.domain.model.TaskAdjustmentStatus;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.repository.TaskAdjustmentRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 执行中调整方向的 MyBatis 仓储。 */
@Repository
public class MyBatisTaskAdjustmentRepository implements TaskAdjustmentRepository {

    private final TaskAdjustmentMapper mapper;

    public MyBatisTaskAdjustmentRepository(TaskAdjustmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TaskAdjustment> findById(TaskAdjustmentId id) {
        return Optional.ofNullable(mapper.selectById(id.value()))
                .map(MyBatisTaskAdjustmentRepository::toDomain);
    }

    @Override
    public List<TaskAdjustment> findByTask(TaskId taskId) {
        return mapper.selectByTask(taskId.value()).stream()
                .map(MyBatisTaskAdjustmentRepository::toDomain)
                .toList();
    }

    @Override
    public List<TaskAdjustment> findPendingByTask(TaskId taskId) {
        return mapper.selectPendingByTask(taskId.value()).stream()
                .map(MyBatisTaskAdjustmentRepository::toDomain)
                .toList();
    }

    @Override
    public boolean hasPending(TaskId taskId) {
        return mapper.countPending(taskId.value()) > 0;
    }

    @Override
    public TaskAdjustment save(TaskAdjustment adjustment) {
        TaskAdjustmentData data = toData(adjustment);
        mapper.insert(data);
        adjustment.assignIdentity(TaskAdjustmentId.of(data.getId()));
        return adjustment;
    }

    @Override
    public void markApplied(TaskAdjustmentId id, TaskRunId runId, Instant appliedAt) {
        int affected = mapper.markApplied(id.value(), runId.value(), toDb(appliedAt));
        if (affected == 0) {
            throw new DomainException("调整方向已经下发或不存在：" + id);
        }
    }

    private static TaskAdjustmentData toData(TaskAdjustment adjustment) {
        TaskAdjustmentData data = new TaskAdjustmentData();
        data.setTaskId(adjustment.getTaskId().value());
        data.setSourceType(adjustment.getSource().name());
        data.setSourceTaskId(adjustment.getSourceTaskId().map(TaskId::value).orElse(null));
        data.setSourceTitle(adjustment.getSourceTitle().orElse(null));
        data.setContent(adjustment.getContent());
        data.setStatus(adjustment.getStatus().name());
        data.setAppliedRunId(adjustment.getAppliedRunId().map(TaskRunId::value).orElse(null));
        data.setAppliedAt(toDb(adjustment.getAppliedAt().orElse(null)));
        data.setCreatedAt(toDb(adjustment.getCreatedAt()));
        return data;
    }

    private static TaskAdjustment toDomain(TaskAdjustmentData data) {
        return TaskAdjustment.restore(
                TaskAdjustmentId.of(data.getId()),
                TaskId.of(data.getTaskId()),
                TaskAdjustmentSource.valueOf(data.getSourceType()),
                data.getSourceTaskId() == null ? null : TaskId.of(data.getSourceTaskId()),
                data.getSourceTitle(),
                data.getContent(),
                TaskAdjustmentStatus.valueOf(data.getStatus()),
                data.getAppliedRunId() == null ? null : TaskRunId.of(data.getAppliedRunId()),
                fromDb(data.getCreatedAt()),
                fromDb(data.getAppliedAt()));
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
