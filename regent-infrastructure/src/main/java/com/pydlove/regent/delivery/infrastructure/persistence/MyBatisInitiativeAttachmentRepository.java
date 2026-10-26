package com.pydlove.regent.delivery.infrastructure.persistence;

import com.pydlove.regent.delivery.domain.model.InitiativeAttachment;
import com.pydlove.regent.delivery.domain.model.InitiativeAttachmentId;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.repository.InitiativeAttachmentRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 立项会议纪要附件仓储的 MyBatis 实现。 */
@Repository
public class MyBatisInitiativeAttachmentRepository
        implements InitiativeAttachmentRepository {

    private final InitiativeAttachmentMapper mapper;

    public MyBatisInitiativeAttachmentRepository(InitiativeAttachmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public InitiativeAttachment save(InitiativeAttachment attachment) {
        InitiativeAttachmentData data = toData(attachment);
        if (mapper.insert(data) != 1) {
            throw new DomainException("立项附件保存失败");
        }
        return attachment.withIdentity(InitiativeAttachmentId.of(data.getId()));
    }

    @Override
    public Optional<InitiativeAttachment> findById(InitiativeAttachmentId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public Optional<InitiativeAttachment> findByIdOwned(
            InitiativeAttachmentId id, UserId ownerId) {
        return Optional.ofNullable(mapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(this::toDomain);
    }

    @Override
    public List<InitiativeAttachment> findByInitiative(InitiativeId initiativeId) {
        return mapper.selectByInitiative(initiativeId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    private InitiativeAttachmentData toData(InitiativeAttachment attachment) {
        InitiativeAttachmentData data = new InitiativeAttachmentData();
        data.setInitiativeId(attachment.initiativeId().value());
        data.setFilename(attachment.filename());
        data.setContentType(attachment.contentType());
        data.setSizeBytes(attachment.sizeBytes());
        data.setStoragePath(attachment.storagePath());
        data.setUploadedBy(attachment.uploadedBy().value());
        data.setCreatedAt(LocalDateTime.ofInstant(attachment.createdAt(), ZoneOffset.UTC));
        return data;
    }

    private InitiativeAttachment toDomain(InitiativeAttachmentData data) {
        return new InitiativeAttachment(
                InitiativeAttachmentId.of(data.getId()),
                InitiativeId.of(data.getInitiativeId()),
                data.getFilename(),
                data.getContentType(),
                data.getSizeBytes(),
                data.getStoragePath(),
                UserId.of(data.getUploadedBy()),
                data.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
