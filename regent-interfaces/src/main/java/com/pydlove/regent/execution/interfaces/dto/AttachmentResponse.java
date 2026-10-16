package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.TaskAttachment;

/**
 * 附件的对外契约。
 *
 * <p>不带 {@code storagePath}：平台把图片放哪是本机实现细节，暴露出去只会让人绕过接口直接读盘。
 * 要看图就访问 {@code contentUrl}，那个地址才能保证「能看图的人」与「能看任务的人」是同一批。
 */
public record AttachmentResponse(
        long id,
        long taskId,
        String filename,
        String contentType,
        long sizeBytes,
        String contentUrl) {

    public static AttachmentResponse from(TaskAttachment attachment) {
        return new AttachmentResponse(
                attachment.id().value(),
                attachment.taskId().value(),
                attachment.filename(),
                attachment.contentType(),
                attachment.sizeBytes(),
                "/api/attachments/" + attachment.id().value() + "/content");
    }
}
