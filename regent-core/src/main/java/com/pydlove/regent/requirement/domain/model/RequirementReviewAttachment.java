package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/** 需求评审会议纪要附件。 */
public record RequirementReviewAttachment(
        RequirementReviewAttachmentId id,
        RequirementReviewRecordId recordId,
        String filename,
        String contentType,
        long sizeBytes,
        String storagePath,
        UserId uploadedBy,
        Instant createdAt) {

    public RequirementReviewAttachment {
        requireNotNull(recordId, "需求评审记录");
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

    public static RequirementReviewAttachment create(RequirementReviewRecordId recordId,
                                                     String filename,
                                                     String contentType,
                                                     long sizeBytes,
                                                     String storagePath,
                                                     UserId uploadedBy) {
        return new RequirementReviewAttachment(
                null, recordId, filename, contentType, sizeBytes, storagePath,
                uploadedBy, Instant.now());
    }

    public RequirementReviewAttachment withIdentity(RequirementReviewAttachmentId assigned) {
        return new RequirementReviewAttachment(
                requireNotNull(assigned, "附件 ID"), recordId, filename, contentType,
                sizeBytes, storagePath, uploadedBy, createdAt);
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
