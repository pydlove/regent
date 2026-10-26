package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/** 概要设计或模块详细设计附件。moduleId 为空时表示概要设计。 */
public record RequirementDesignAttachment(
        RequirementDesignAttachmentId id,
        RequirementDeliveryId deliveryId,
        RequirementDesignModuleId moduleId,
        String filename,
        String contentType,
        long sizeBytes,
        String storagePath,
        UserId uploadedBy,
        Instant createdAt) {

    public RequirementDesignAttachment {
        requireNotNull(deliveryId, "需求交付");
        filename = requireText(filename, "附件文件名");
        if (filename.length() > 255) {
            throw new DomainException("附件文件名不能超过 255 个字符");
        }
        contentType = requireText(contentType, "附件类型");
        if (sizeBytes <= 0) throw new DomainException("附件大小必须为正数");
        storagePath = requireText(storagePath, "附件存储路径");
        requireNotNull(uploadedBy, "上传人");
        requireNotNull(createdAt, "上传时间");
    }

    public static RequirementDesignAttachment create(
            RequirementDeliveryId deliveryId,
            RequirementDesignModuleId moduleId,
            String filename,
            String contentType,
            long sizeBytes,
            String storagePath,
            UserId uploadedBy) {
        return new RequirementDesignAttachment(
                null, deliveryId, moduleId, filename, contentType,
                sizeBytes, storagePath, uploadedBy, Instant.now());
    }

    public RequirementDesignAttachment withIdentity(RequirementDesignAttachmentId assigned) {
        if (id != null) throw new DomainException("设计附件已有 ID，不能重复分配");
        return new RequirementDesignAttachment(
                requireNotNull(assigned, "设计附件 ID"), deliveryId, moduleId,
                filename, contentType, sizeBytes, storagePath, uploadedBy, createdAt);
    }

    public boolean outline() {
        return moduleId == null;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value.trim();
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) throw new DomainException(field + "不能为空");
        return value;
    }
}
