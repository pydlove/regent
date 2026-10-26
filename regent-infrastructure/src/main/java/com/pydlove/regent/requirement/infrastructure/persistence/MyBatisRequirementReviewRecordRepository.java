package com.pydlove.regent.requirement.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.requirement.domain.model.RequirementReviewCandidate;
import com.pydlove.regent.requirement.domain.model.RequirementReviewRecord;
import com.pydlove.regent.requirement.domain.model.RequirementReviewRecordId;
import com.pydlove.regent.requirement.domain.model.RequirementReviewStatus;
import com.pydlove.regent.requirement.domain.repository.RequirementReviewRecordRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 线下需求评审记录仓储的 MyBatis 实现。 */
@Repository
public class MyBatisRequirementReviewRecordRepository
        implements RequirementReviewRecordRepository {

    private static final TypeReference<List<RequirementReviewCandidate>> REQUIREMENTS =
            new TypeReference<>() {
            };

    private final RequirementReviewRecordMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisRequirementReviewRecordRepository(RequirementReviewRecordMapper mapper,
                                                    ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<RequirementReviewRecord> findById(RequirementReviewRecordId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public List<RequirementReviewRecord> findByVersion(ProductVersionId versionId) {
        return mapper.selectByVersion(versionId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int nextReviewNo(ProductVersionId versionId) {
        return mapper.selectNextReviewNo(versionId.value());
    }

    @Override
    public void save(RequirementReviewRecord record) {
        RequirementReviewRecordData data = toData(record);
        if (mapper.insert(data) != 1) {
            throw new DomainException("需求评审记录保存失败");
        }
        record.assignIdentity(RequirementReviewRecordId.of(data.getId()));
    }

    @Override
    public void update(RequirementReviewRecord record) {
        if (mapper.update(toData(record)) != 1) {
            throw new DomainException("需求评审记录更新失败：" + record.getId().value());
        }
    }

    private RequirementReviewRecord toDomain(RequirementReviewRecordData data) {
        try {
            return RequirementReviewRecord.restore(
                    RequirementReviewRecordId.of(data.getId()),
                    ProductVersionId.of(data.getVersionId()),
                    data.getReviewNo(),
                    data.getReviewDate(),
                    data.getParticipants(),
                    data.getConclusion(),
                    data.getMinutes(),
                    RequirementReviewStatus.valueOf(data.getStatus()),
                    data.getReviewComment(),
                    objectMapper.readValue(data.getRequirementsJson(), REQUIREMENTS),
                    UserId.of(data.getReviewedBy()),
                    data.getDecidedBy() == null ? null : UserId.of(data.getDecidedBy()),
                    instant(data.getDecidedAt()),
                    instant(data.getCreatedAt()),
                    instant(data.getUpdatedAt()));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new DomainException("需求评审记录读取失败：" + data.getId(), exception);
        }
    }

    private RequirementReviewRecordData toData(RequirementReviewRecord record) {
        RequirementReviewRecordData data = new RequirementReviewRecordData();
        data.setId(record.getId() == null ? null : record.getId().value());
        data.setVersionId(record.getVersionId().value());
        data.setReviewNo(record.getReviewNo());
        data.setReviewDate(record.getReviewDate());
        data.setParticipants(record.getParticipants());
        data.setConclusion(record.getConclusion());
        data.setMinutes(record.getMinutes());
        data.setStatus(record.getStatus().name());
        data.setReviewComment(record.getReviewComment());
        data.setRequirementsJson(json(record.getRequirements()));
        data.setReviewedBy(record.getSubmittedBy().value());
        data.setDecidedBy(record.getDecidedBy() == null
                ? null
                : record.getDecidedBy().value());
        data.setDecidedAt(db(record.getDecidedAt()));
        data.setCreatedAt(db(record.getCreatedAt()));
        data.setUpdatedAt(db(record.getUpdatedAt()));
        return data;
    }

    private String json(List<RequirementReviewCandidate> requirements) {
        try {
            return objectMapper.writeValueAsString(requirements);
        } catch (JsonProcessingException exception) {
            throw new DomainException("需求评审需求快照序列化失败", exception);
        }
    }

    private static LocalDateTime db(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
