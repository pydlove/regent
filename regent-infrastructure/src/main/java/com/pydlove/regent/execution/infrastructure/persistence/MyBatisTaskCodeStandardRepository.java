package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.CodeStandardId;
import com.pydlove.regent.execution.domain.model.CodeStandardVersionId;
import com.pydlove.regent.execution.domain.model.TaskCodeStandard;
import com.pydlove.regent.execution.domain.model.TaskCodeStandardId;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskCodeStandardRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 任务开发规范快照仓储实现。 */
@Repository
public class MyBatisTaskCodeStandardRepository implements TaskCodeStandardRepository {

    private final TaskCodeStandardMapper mapper;

    public MyBatisTaskCodeStandardRepository(TaskCodeStandardMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public TaskCodeStandard save(TaskCodeStandard standard) {
        TaskCodeStandardData data = toData(standard);
        mapper.insert(data);
        return standard.withIdentity(TaskCodeStandardId.of(data.getId()));
    }

    @Override
    public List<TaskCodeStandard> findByTask(TaskId taskId) {
        return mapper.selectByTask(taskId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<TaskCodeStandard> findByIdOwned(TaskCodeStandardId id, UserId ownerId) {
        return Optional.ofNullable(mapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(this::toDomain);
    }

    private TaskCodeStandardData toData(TaskCodeStandard standard) {
        TaskCodeStandardData data = new TaskCodeStandardData();
        data.setTaskId(standard.taskId().value());
        data.setStandardId(standard.standardId().value());
        data.setStandardVersionId(standard.standardVersionId().value());
        data.setStandardName(standard.standardName());
        data.setVersionNo(standard.versionNo());
        data.setFilename(standard.filename());
        data.setContentType(standard.contentType());
        data.setSizeBytes(standard.sizeBytes());
        data.setContentHash(standard.contentHash());
        data.setStoragePath(standard.storagePath());
        data.setSourceProjectId(standard.sourceProjectId() == null
                ? null : standard.sourceProjectId().value());
        data.setCreatedAt(LocalDateTime.ofInstant(standard.createdAt(), ZoneOffset.UTC));
        return data;
    }

    private TaskCodeStandard toDomain(TaskCodeStandardData data) {
        return new TaskCodeStandard(
                TaskCodeStandardId.of(data.getId()),
                TaskId.of(data.getTaskId()),
                CodeStandardId.of(data.getStandardId()),
                CodeStandardVersionId.of(data.getStandardVersionId()),
                data.getStandardName(),
                data.getVersionNo(),
                data.getFilename(),
                data.getContentType(),
                data.getSizeBytes(),
                data.getContentHash(),
                data.getStoragePath(),
                data.getSourceProjectId() == null ? null : ProjectId.of(data.getSourceProjectId()),
                fromDb(data.getCreatedAt()));
    }

    private static Instant fromDb(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }
}
