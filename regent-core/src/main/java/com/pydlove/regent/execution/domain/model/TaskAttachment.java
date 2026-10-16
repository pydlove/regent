package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/**
 * 任务的一张图片附件。
 *
 * <p>它是一份不可变的事实：贴进来之后内容不再改，所以没有状态迁移、没有 setter，
 * 落库回填主键也走 {@link #withIdentity} 返回新实例，而不是像 {@code Task} 那样原地赋值 ——
 * 可变是为了状态机，这里没有状态机。
 *
 * <p>领域只守「这是一张图片」这条结构性不变量（{@code image/*}）；具体放行哪几种格式、
 * 图片是否真的是图片（魔数）、单张多大算超限，都是应用服务的策略，见 {@code TaskAttachmentService}。
 *
 * @param id          落库前为 null
 * @param filename    用户的原始文件名，只用于展示 —— 磁盘上不落这个名字，理由见 AttachmentStorage
 * @param contentType 形如 {@code image/png}
 * @param sizeBytes   字节数，必须为正
 * @param storagePath 平台侧绝对路径，派发时交给 {@code codex exec -i}
 */
public record TaskAttachment(
        AttachmentId id,
        TaskId taskId,
        String filename,
        String contentType,
        long sizeBytes,
        String storagePath,
        Instant createdAt) {

    private static final int MAX_FILENAME = 255;

    public TaskAttachment {
        requireNotNull(taskId, "附件所属任务");
        filename = requireFilename(filename);
        contentType = requireNotNull(contentType, "附件类型");
        if (!contentType.startsWith("image/")) {
            throw new DomainException("附件只支持图片： " + contentType);
        }
        if (sizeBytes <= 0) {
            throw new DomainException("附件大小必须为正：" + sizeBytes);
        }
        storagePath = requireText(storagePath, "附件存储路径");
        requireNotNull(createdAt, "创建时间");
    }

    /** 新上传、尚未落库的附件。 */
    public static TaskAttachment of(TaskId taskId, String filename, String contentType,
                                    long sizeBytes, String storagePath) {
        return new TaskAttachment(null, taskId, filename, contentType, sizeBytes, storagePath, Instant.now());
    }

    /** 落库后回填数据库生成的主键。重复分配直接报错，避免两条记录悄悄并成一条。 */
    public TaskAttachment withIdentity(AttachmentId assigned) {
        if (this.id != null) {
            throw new DomainException("附件已有 ID，不能重复分配：" + this.id);
        }
        return new TaskAttachment(requireNotNull(assigned, "附件 ID"), taskId, filename,
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
