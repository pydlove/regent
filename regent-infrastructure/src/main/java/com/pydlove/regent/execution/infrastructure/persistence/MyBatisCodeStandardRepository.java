package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.CodeStandard;
import com.pydlove.regent.execution.domain.model.CodeStandardId;
import com.pydlove.regent.execution.domain.model.CodeStandardScope;
import com.pydlove.regent.execution.domain.model.CodeStandardVersionId;
import com.pydlove.regent.execution.domain.repository.CodeStandardRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 项目开发规范条目仓储实现。 */
@Repository
public class MyBatisCodeStandardRepository implements CodeStandardRepository {

    private final CodeStandardMapper mapper;

    public MyBatisCodeStandardRepository(CodeStandardMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CodeStandard save(CodeStandard standard) {
        CodeStandardData data = toData(standard);
        if (standard.id() == null) {
            mapper.insert(data);
            return standard.withIdentity(CodeStandardId.of(data.getId()));
        }
        if (mapper.update(data) != 1) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "开发规范更新失败，可能已归档或不存在：" + standard.id());
        }
        return standard;
    }

    @Override
    public Optional<CodeStandard> findById(CodeStandardId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public Optional<CodeStandard> findByIdOwned(CodeStandardId id, UserId ownerId) {
        return Optional.ofNullable(mapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(this::toDomain);
    }

    @Override
    public List<CodeStandard> findByProject(ProjectId projectId) {
        return mapper.selectByProject(projectId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<CodeStandard> findByProjectAndName(ProjectId projectId, String name) {
        return Optional.ofNullable(mapper.selectByProjectAndName(projectId.value(), name))
                .map(this::toDomain);
    }

    private CodeStandardData toData(CodeStandard standard) {
        CodeStandardData data = new CodeStandardData();
        data.setId(standard.id() == null ? null : standard.id().value());
        data.setProjectId(standard.projectId().value());
        data.setName(standard.name());
        data.setDescription(standard.description());
        data.setScope(standard.scope().name());
        data.setEnabled(standard.enabled());
        data.setDefaultApply(standard.defaultApply());
        data.setSortOrder(standard.sortOrder());
        data.setArchived(standard.archived());
        data.setSourceProjectId(standard.sourceProjectId() == null
                ? null : standard.sourceProjectId().value());
        data.setSourceStandardId(standard.sourceStandardId() == null
                ? null : standard.sourceStandardId().value());
        data.setSourceVersionId(standard.sourceVersionId() == null
                ? null : standard.sourceVersionId().value());
        data.setSourceVersionNo(standard.sourceVersionNo());
        data.setCreatedAt(toDb(standard.createdAt()));
        data.setUpdatedAt(toDb(standard.updatedAt()));
        return data;
    }

    private CodeStandard toDomain(CodeStandardData data) {
        return new CodeStandard(
                CodeStandardId.of(data.getId()),
                ProjectId.of(data.getProjectId()),
                data.getName(),
                data.getDescription(),
                CodeStandardScope.valueOf(data.getScope()),
                Boolean.TRUE.equals(data.getEnabled()),
                Boolean.TRUE.equals(data.getDefaultApply()),
                data.getSortOrder(),
                Boolean.TRUE.equals(data.getArchived()),
                data.getSourceProjectId() == null ? null : ProjectId.of(data.getSourceProjectId()),
                data.getSourceStandardId() == null ? null : CodeStandardId.of(data.getSourceStandardId()),
                data.getSourceVersionId() == null
                        ? null : CodeStandardVersionId.of(data.getSourceVersionId()),
                data.getSourceVersionNo(),
                fromDb(data.getCreatedAt()),
                fromDb(data.getUpdatedAt()));
    }

    private static LocalDateTime toDb(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant fromDb(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }
}
