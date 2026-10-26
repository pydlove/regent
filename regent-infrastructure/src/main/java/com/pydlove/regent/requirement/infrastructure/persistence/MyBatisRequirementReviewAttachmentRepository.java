package com.pydlove.regent.requirement.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.requirement.domain.model.RequirementReviewAttachment;
import com.pydlove.regent.requirement.domain.model.RequirementReviewAttachmentId;
import com.pydlove.regent.requirement.domain.model.RequirementReviewRecordId;
import com.pydlove.regent.requirement.domain.repository.RequirementReviewAttachmentRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 需求评审附件仓储的 MyBatis 实现。 */
@Repository
public class MyBatisRequirementReviewAttachmentRepository
        implements RequirementReviewAttachmentRepository {

    private final RequirementReviewAttachmentMapper mapper;

    public MyBatisRequirementReviewAttachmentRepository(RequirementReviewAttachmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RequirementReviewAttachment save(RequirementReviewAttachment attachment) {
        RequirementReviewAttachmentData data = toData(attachment);
        if (mapper.insert(data) != 1) {
            throw new DomainException("需求评审附件保存失败");
        }
        return attachment.withIdentity(RequirementReviewAttachmentId.of(data.getId()));
    }

    @Override
    public Optional<RequirementReviewAttachment> findById(RequirementReviewAttachmentId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public List<RequirementReviewAttachment> findByRecord(RequirementReviewRecordId recordId) {
        return mapper.selectByRecord(recordId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    private RequirementReviewAttachment toDomain(RequirementReviewAttachmentData data) {
        return new RequirementReviewAttachment(
                RequirementReviewAttachmentId.of(data.getId()),
                RequirementReviewRecordId.of(data.getRecordId()),
                data.getFilename(),
                data.getContentType(),
                data.getSizeBytes(),
                data.getStoragePath(),
                UserId.of(data.getUploadedBy()),
                instant(data.getCreatedAt()));
    }

    private RequirementReviewAttachmentData toData(RequirementReviewAttachment attachment) {
        RequirementReviewAttachmentData data = new RequirementReviewAttachmentData();
        data.setId(attachment.id() == null ? null : attachment.id().value());
        data.setRecordId(attachment.recordId().value());
        data.setFilename(attachment.filename());
        data.setContentType(attachment.contentType());
        data.setSizeBytes(attachment.sizeBytes());
        data.setStoragePath(attachment.storagePath());
        data.setUploadedBy(attachment.uploadedBy().value());
        data.setCreatedAt(db(attachment.createdAt()));
        return data;
    }

    private static LocalDateTime db(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
