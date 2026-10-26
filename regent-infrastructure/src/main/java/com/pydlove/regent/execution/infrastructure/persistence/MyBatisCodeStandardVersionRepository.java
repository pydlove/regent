package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.CodeStandardId;
import com.pydlove.regent.execution.domain.model.CodeStandardVersion;
import com.pydlove.regent.execution.domain.model.CodeStandardVersionId;
import com.pydlove.regent.execution.domain.model.LibraryDocumentId;
import com.pydlove.regent.execution.domain.repository.CodeStandardVersionRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 开发规范版本仓储实现。 */
@Repository
public class MyBatisCodeStandardVersionRepository implements CodeStandardVersionRepository {

    private final CodeStandardVersionMapper mapper;

    public MyBatisCodeStandardVersionRepository(CodeStandardVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CodeStandardVersion save(CodeStandardVersion version) {
        CodeStandardVersionData data = toData(version);
        mapper.insert(data);
        return version.withIdentity(CodeStandardVersionId.of(data.getId()));
    }

    @Override
    public Optional<CodeStandardVersion> findById(CodeStandardVersionId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public Optional<CodeStandardVersion> findByIdOwned(CodeStandardVersionId id, UserId ownerId) {
        return Optional.ofNullable(mapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(this::toDomain);
    }

    @Override
    public Optional<CodeStandardVersion> findCurrent(CodeStandardId standardId) {
        return Optional.ofNullable(mapper.selectCurrent(standardId.value())).map(this::toDomain);
    }

    @Override
    public List<CodeStandardVersion> findByStandard(CodeStandardId standardId) {
        return mapper.selectByStandard(standardId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public int nextVersionNo(CodeStandardId standardId) {
        Integer current = mapper.selectMaxVersionNo(standardId.value());
        return current == null ? 1 : current + 1;
    }

    private CodeStandardVersionData toData(CodeStandardVersion version) {
        CodeStandardVersionData data = new CodeStandardVersionData();
        data.setStandardId(version.standardId().value());
        data.setProjectId(version.projectId().value());
        data.setVersionNo(version.versionNo());
        data.setLibraryDocumentId(version.libraryDocumentId().value());
        data.setContentHash(version.contentHash());
        data.setCreatedAt(LocalDateTime.ofInstant(version.createdAt(), ZoneOffset.UTC));
        return data;
    }

    private CodeStandardVersion toDomain(CodeStandardVersionData data) {
        return new CodeStandardVersion(
                CodeStandardVersionId.of(data.getId()),
                CodeStandardId.of(data.getStandardId()),
                ProjectId.of(data.getProjectId()),
                data.getVersionNo(),
                LibraryDocumentId.of(data.getLibraryDocumentId()),
                data.getFilename(),
                data.getContentType(),
                data.getSizeBytes(),
                data.getContentHash(),
                data.getStoragePath(),
                fromDb(data.getCreatedAt()));
    }

    private static Instant fromDb(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }
}
