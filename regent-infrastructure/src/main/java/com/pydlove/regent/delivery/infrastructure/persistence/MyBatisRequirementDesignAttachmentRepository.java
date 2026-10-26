package com.pydlove.regent.delivery.infrastructure.persistence;

import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.model.RequirementDesignAttachment;
import com.pydlove.regent.delivery.domain.model.RequirementDesignAttachmentId;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModuleId;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignAttachmentRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisRequirementDesignAttachmentRepository
        implements RequirementDesignAttachmentRepository {

    private final RequirementDesignAttachmentMapper mapper;

    public MyBatisRequirementDesignAttachmentRepository(
            RequirementDesignAttachmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RequirementDesignAttachment save(RequirementDesignAttachment attachment) {
        RequirementDesignAttachmentData data = toData(attachment);
        if (attachment.id() == null) {
            if (mapper.insert(data) != 1) {
                throw new DomainException("设计附件保存失败");
            }
            return attachment.withIdentity(
                    RequirementDesignAttachmentId.of(data.getId()));
        }
        return attachment;
    }

    @Override
    public Optional<RequirementDesignAttachment> findById(
            RequirementDesignAttachmentId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public List<RequirementDesignAttachment> findByDelivery(
            RequirementDeliveryId deliveryId) {
        return mapper.selectByDelivery(deliveryId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<RequirementDesignAttachment> findByModule(
            RequirementDesignModuleId moduleId) {
        return mapper.selectByModule(moduleId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    private RequirementDesignAttachment toDomain(
            RequirementDesignAttachmentData data) {
        return new RequirementDesignAttachment(
                RequirementDesignAttachmentId.of(data.getId()),
                RequirementDeliveryId.of(data.getDeliveryId()),
                data.getModuleId() == null
                        ? null
                        : RequirementDesignModuleId.of(data.getModuleId()),
                data.getFilename(),
                data.getContentType(),
                data.getSizeBytes(),
                data.getStoragePath(),
                UserId.of(data.getUploadedBy()),
                DeliveryTime.fromDb(data.getCreatedAt()));
    }

    private RequirementDesignAttachmentData toData(
            RequirementDesignAttachment attachment) {
        RequirementDesignAttachmentData data = new RequirementDesignAttachmentData();
        data.setId(attachment.id() == null ? null : attachment.id().value());
        data.setDeliveryId(attachment.deliveryId().value());
        data.setModuleId(attachment.moduleId() == null
                ? null
                : attachment.moduleId().value());
        data.setFilename(attachment.filename());
        data.setContentType(attachment.contentType());
        data.setSizeBytes(attachment.sizeBytes());
        data.setStoragePath(attachment.storagePath());
        data.setUploadedBy(attachment.uploadedBy().value());
        data.setCreatedAt(DeliveryTime.toDb(attachment.createdAt()));
        return data;
    }
}
