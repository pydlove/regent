package com.pydlove.regent.requirement.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.requirement.domain.model.RequirementStatus;
import com.pydlove.regent.requirement.domain.model.RequirementPoolStatus;
import com.pydlove.regent.requirement.domain.model.RequirementSource;
import com.pydlove.regent.requirement.domain.model.RequirementTaskDraft;
import com.pydlove.regent.requirement.domain.model.RequirementTrashItem;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 需求仓储 MyBatis 实现。 */
@Repository
public class MyBatisRequirementRepository implements RequirementRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<RequirementTaskDraft>> TASK_DRAFTS = new TypeReference<>() {
    };

    private final RequirementMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisRequirementRepository(RequirementMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Requirement> findById(RequirementId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public Optional<Requirement> findByIdOwned(RequirementId id, UserId ownerId) {
        return Optional.ofNullable(mapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(this::toDomain);
    }

    @Override
    public List<Requirement> findByProject(ProjectId projectId) {
        return mapper.selectByProject(projectId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Requirement> findByProject(ProjectId projectId, UserId ownerId) {
        return mapper.selectByProjectOwned(projectId.value(), ownerId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Requirement> findAll(UserId ownerId) {
        return mapper.selectAllOwned(ownerId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<RequirementTrashItem> findDeletedByProject(ProjectId projectId, UserId ownerId) {
        return mapper.selectDeletedByProjectOwned(projectId.value(), ownerId.value()).stream()
                .map(this::toTrashItem)
                .toList();
    }

    @Override
    public Optional<RequirementTrashItem> findDeletedByIdOwned(RequirementId id, UserId ownerId) {
        return Optional.ofNullable(mapper.selectDeletedByIdOwned(id.value(), ownerId.value()))
                .map(this::toTrashItem);
    }

    @Override
    public void moveToTrash(RequirementId id, UserId deletedBy, Instant deletedAt) {
        if (mapper.moveToTrash(id.value(), deletedBy.value(), db(deletedAt)) != 1) {
            throw new DomainException("需求删除失败或已删除：" + id.value());
        }
    }

    @Override
    public void restore(RequirementId id, Instant updatedAt) {
        if (mapper.restore(id.value(), db(updatedAt)) != 1) {
            throw new DomainException("需求恢复失败或已恢复：" + id.value());
        }
    }

    @Override
    public void save(Requirement requirement) {
        RequirementData data = toData(requirement);
        if (requirement.getId() == null) {
            if (mapper.insert(data) != 1) {
                throw new DomainException("需求新增失败");
            }
            requirement.assignIdentity(RequirementId.of(data.getId()));
            return;
        }
        if (mapper.update(data) != 1) {
            throw new DomainException("需求更新失败或已删除：" + requirement.getId());
        }
    }

    private RequirementData toData(Requirement requirement) {
        RequirementData data = new RequirementData();
        data.setId(requirement.getId() == null ? null : requirement.getId().value());
        data.setProjectId(requirement.getProjectId().value());
        data.setInitiativeId(requirement.getInitiativeId() == null
                ? null
                : requirement.getInitiativeId().value());
        data.setVersionId(requirement.getVersionId() == null
                ? null
                : requirement.getVersionId().value());
        data.setTitle(requirement.getTitle());
        data.setRawText(requirement.getRawText());
        data.setAcceptanceText(requirement.getAcceptanceText());
        data.setClarificationText(requirement.getClarificationText());
        data.setStatus(requirement.getStatus().name());
        data.setPoolStatus(requirement.getPoolStatus().name());
        data.setSource(requirement.getSource().name());
        data.setReviewComment(requirement.getReviewComment());
        data.setReviewedBy(requirement.getReviewedBy() == null
                ? null
                : requirement.getReviewedBy().value());
        data.setReviewedAt(db(requirement.getReviewedAt()));
        data.setClarificationQuestions(json(requirement.getClarificationQuestions()));
        data.setAssumptions(json(requirement.getAssumptions()));
        data.setTaskDrafts(json(requirement.getTaskDrafts()));
        data.setPlannedAt(db(requirement.getPlannedAt()));
        data.setClarifiedAt(db(requirement.getStatus() == RequirementStatus.READY
                || requirement.getStatus() == RequirementStatus.PLANNED
                ? requirement.getUpdatedAt() : null));
        data.setCreatedAt(db(requirement.getCreatedAt()));
        data.setUpdatedAt(db(requirement.getUpdatedAt()));
        return data;
    }

    private Requirement toDomain(RequirementData data) {
        try {
            return Requirement.restore(
                    RequirementId.of(data.getId()),
                    ProjectId.of(data.getProjectId()),
                    data.getInitiativeId() == null
                            ? null
                            : InitiativeId.of(data.getInitiativeId()),
                    data.getVersionId() == null
                            ? null
                            : ProductVersionId.of(data.getVersionId()),
                    data.getTitle(),
                    data.getRawText(),
                    data.getAcceptanceText(),
                    data.getClarificationText(),
                    RequirementStatus.valueOf(data.getStatus()),
                    RequirementPoolStatus.valueOf(data.getPoolStatus()),
                    RequirementSource.valueOf(data.getSource()),
                    data.getReviewComment(),
                    data.getReviewedBy() == null ? null : UserId.of(data.getReviewedBy()),
                    instant(data.getReviewedAt()),
                    list(data.getClarificationQuestions(), STRING_LIST),
                    list(data.getAssumptions(), STRING_LIST),
                    list(data.getTaskDrafts(), TASK_DRAFTS),
                    instant(data.getCreatedAt()),
                    instant(data.getUpdatedAt()),
                    instant(data.getPlannedAt()));
        } catch (RuntimeException e) {
            throw new DomainException("需求数据读取失败：" + data.getId(), e);
        }
    }

    private RequirementTrashItem toTrashItem(RequirementData data) {
        return new RequirementTrashItem(
                toDomain(data),
                instant(data.getDeletedAt()),
                data.getDeletedBy() == null ? null : UserId.of(data.getDeletedBy()));
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new DomainException("需求扩展数据序列化失败", e);
        }
    }

    private <T> List<T> list(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new DomainException("需求扩展数据格式不正确", e);
        }
    }

    private static LocalDateTime db(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
