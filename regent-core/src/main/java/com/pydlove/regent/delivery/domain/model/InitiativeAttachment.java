package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/** 立项审批过程中的会议纪要附件。 */
public record InitiativeAttachment(
        InitiativeAttachmentId id,
        InitiativeId initiativeId,
        String filename,
        String contentType,
        long sizeBytes,
        String storagePath,
        UserId uploadedBy,
        Instant createdAt) {

    public InitiativeAttachment {
        requireNotNull(initiativeId, "所属立项");
        filename = requireText(filename, "附件文件名");
        if (filename.length() > 255) {
            throw new DomainException("附件文件名不能超过 255 个字符");
        }
        contentType = requireText(contentType, "附件类型");
        if (sizeBytes <= 0) {
            throw new DomainException("附件大小必须为正数");
        }
        storagePath = requireText(storagePath, "附件存储路径");
        requireNotNull(uploadedBy, "上传人");
        requireNotNull(createdAt, "上传时间");
    }

    public static InitiativeAttachment create(InitiativeId initiativeId,
                                              String filename,
                                              String contentType,
                                              long sizeBytes,
                                              String storagePath,
                                              UserId uploadedBy) {
        return new InitiativeAttachment(
                null, initiativeId, filename, contentType, sizeBytes, storagePath,
                uploadedBy, Instant.now());
    }

    public InitiativeAttachment withIdentity(InitiativeAttachmentId assigned) {
        if (id != null) {
            throw new DomainException("立项附件已有 ID，不能重复分配");
        }
        return new InitiativeAttachment(
                requireNotNull(assigned, "立项附件 ID"), initiativeId, filename,
                contentType, sizeBytes, storagePath, uploadedBy, createdAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value.trim();
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }
}
