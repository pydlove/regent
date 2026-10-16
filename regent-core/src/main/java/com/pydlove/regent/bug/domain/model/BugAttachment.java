package com.pydlove.regent.bug.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/**
 * Bug 单的一张图片附件。
 *
 * <p>和任务附件一样是不可变事实：只用于补充截图与复现证据，没有状态迁移。
 */
public record BugAttachment(
        BugAttachmentId id,
        BugId bugId,
        String filename,
        String contentType,
        long sizeBytes,
        String storagePath,
        Instant createdAt) {

    private static final int MAX_FILENAME = 255;

    public BugAttachment {
        requireNotNull(bugId, "附件所属 Bug");
        filename = requireFilename(filename);
        contentType = requireNotNull(contentType, "附件类型");
        if (!contentType.startsWith("image/")) {
            throw new DomainException("附件只支持图片：" + contentType);
        }
        if (sizeBytes <= 0) {
            throw new DomainException("附件大小必须为正：" + sizeBytes);
        }
        storagePath = requireText(storagePath, "附件存储路径");
        requireNotNull(createdAt, "创建时间");
    }

    public static BugAttachment of(BugId bugId, String filename, String contentType,
                                   long sizeBytes, String storagePath) {
        return new BugAttachment(null, bugId, filename, contentType, sizeBytes, storagePath, Instant.now());
    }

    public BugAttachment withIdentity(BugAttachmentId assigned) {
        if (id != null) {
            throw new DomainException("Bug 附件已有 ID，不能重复分配：" + id);
        }
        return new BugAttachment(requireNotNull(assigned, "Bug 附件 ID"), bugId, filename,
                contentType, sizeBytes, storagePath, createdAt);
    }

    private static String requireFilename(String filename) {
        requireText(filename, "附件文件名");
        if (filename.length() > MAX_FILENAME) {
            throw new DomainException("附件文件名超长（上限 " + MAX_FILENAME + " 字符）：" + filename.length());
        }
        return filename;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }
}
