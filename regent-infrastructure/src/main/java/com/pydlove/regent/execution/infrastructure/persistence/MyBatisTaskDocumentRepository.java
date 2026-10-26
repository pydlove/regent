package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.LibraryDocumentId;
import com.pydlove.regent.execution.domain.model.TaskDocument;
import com.pydlove.regent.execution.domain.model.TaskDocumentId;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskDocumentRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 任务文档仓储。 */
@Repository
public class MyBatisTaskDocumentRepository implements TaskDocumentRepository {

    private final TaskDocumentMapper mapper;

    public MyBatisTaskDocumentRepository(TaskDocumentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public TaskDocument save(TaskDocument document) {
        TaskDocumentData data = toData(document);
        mapper.insert(data);
        return document.withIdentity(TaskDocumentId.of(data.getId()));
    }

    @Override
    public Optional<TaskDocument> findById(TaskDocumentId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(MyBatisTaskDocumentRepository::toDomain);
    }

    @Override
    public Optional<TaskDocument> findByIdOwned(TaskDocumentId id, UserId ownerId) {
        return Optional.ofNullable(mapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(MyBatisTaskDocumentRepository::toDomain);
    }

    @Override
    public List<TaskDocument> findByTask(TaskId taskId) {
        return mapper.selectByTask(taskId.value()).stream()
                .map(MyBatisTaskDocumentRepository::toDomain)
                .toList();
    }

    private static TaskDocumentData toData(TaskDocument document) {
        TaskDocumentData data = new TaskDocumentData();
        data.setTaskId(document.taskId().value());
        data.setLibraryDocumentId(document.libraryDocumentId() == null
                ? null : document.libraryDocumentId().value());
        data.setFilename(document.filename());
        data.setContentType(document.contentType());
        data.setSizeBytes(document.sizeBytes());
        data.setStoragePath(document.storagePath());
        data.setCreatedAt(toDb(document.createdAt()));
        return data;
    }

    private static TaskDocument toDomain(TaskDocumentData data) {
        return new TaskDocument(
                TaskDocumentId.of(data.getId()),
                TaskId.of(data.getTaskId()),
                data.getLibraryDocumentId() == null
                        ? null : LibraryDocumentId.of(data.getLibraryDocumentId()),
                data.getFilename(),
                data.getContentType(),
                data.getSizeBytes(),
                data.getStoragePath(),
                fromDb(data.getCreatedAt()));
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
