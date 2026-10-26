package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.LibraryDocument;
import com.pydlove.regent.execution.domain.model.LibraryDocumentId;
import com.pydlove.regent.execution.domain.model.LibraryType;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.LibraryDocumentRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 项目文件库仓储。 */
@Repository
public class MyBatisLibraryDocumentRepository implements LibraryDocumentRepository {

    private final LibraryDocumentMapper mapper;

    public MyBatisLibraryDocumentRepository(LibraryDocumentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public LibraryDocument save(LibraryDocument document) {
        LibraryDocumentData data = toData(document);
        mapper.insert(data);
        return document.withIdentity(LibraryDocumentId.of(data.getId()));
    }

    @Override
    public Optional<LibraryDocument> findById(LibraryDocumentId id) {
        return Optional.ofNullable(mapper.selectById(id.value()))
                .map(MyBatisLibraryDocumentRepository::toDomain);
    }

    @Override
    public Optional<LibraryDocument> findByIdOwned(LibraryDocumentId id, UserId ownerId) {
        return Optional.ofNullable(mapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(MyBatisLibraryDocumentRepository::toDomain);
    }

    @Override
    public Optional<LibraryDocument> findByProjectAndHash(ProjectId projectId, String contentHash) {
        return Optional.ofNullable(mapper.selectByProjectAndHash(projectId.value(), contentHash))
                .map(MyBatisLibraryDocumentRepository::toDomain);
    }

    @Override
    public Optional<LibraryDocument> findByProjectAndTypeAndHash(ProjectId projectId,
                                                                 LibraryType libraryType,
                                                                 String contentHash) {
        return Optional.ofNullable(mapper.selectByProjectAndTypeAndHash(
                        projectId.value(), libraryType.name(), contentHash))
                .map(MyBatisLibraryDocumentRepository::toDomain);
    }

    @Override
    public List<LibraryDocument> findByProject(ProjectId projectId) {
        return mapper.selectByProject(projectId.value()).stream()
                .map(MyBatisLibraryDocumentRepository::toDomain)
                .toList();
    }

    @Override
    public List<LibraryDocument> findByProjectAndType(ProjectId projectId, LibraryType libraryType) {
        return mapper.selectByProjectAndType(projectId.value(), libraryType.name()).stream()
                .map(MyBatisLibraryDocumentRepository::toDomain)
                .toList();
    }

    private static LibraryDocumentData toData(LibraryDocument document) {
        LibraryDocumentData data = new LibraryDocumentData();
        data.setProjectId(document.projectId().value());
        data.setLibraryType(document.libraryType().name());
        data.setSourceTaskId(document.sourceTaskId() == null ? null : document.sourceTaskId().value());
        data.setFilename(document.filename());
        data.setContentType(document.contentType());
        data.setSizeBytes(document.sizeBytes());
        data.setContentHash(document.contentHash());
        data.setStoragePath(document.storagePath());
        data.setCreatedAt(toDb(document.createdAt()));
        return data;
    }

    private static LibraryDocument toDomain(LibraryDocumentData data) {
        return new LibraryDocument(
                LibraryDocumentId.of(data.getId()),
                ProjectId.of(data.getProjectId()),
                LibraryType.valueOf(data.getLibraryType()),
                data.getSourceTaskId() == null ? null : TaskId.of(data.getSourceTaskId()),
                data.getFilename(),
                data.getContentType(),
                data.getSizeBytes(),
                data.getContentHash(),
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
