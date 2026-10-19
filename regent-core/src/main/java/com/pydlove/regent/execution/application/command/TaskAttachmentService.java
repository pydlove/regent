package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.AttachmentId;
import com.pydlove.regent.execution.domain.model.TaskAttachment;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskAttachmentRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.kernel.AttachmentScope;
import com.pydlove.regent.shared.kernel.ImageUploadPolicy;

import java.util.List;

/**
 * 任务附件用例：上传、列出、读回内容。
 *
 * <p>校验分三层，各守各的：
 * <ol>
 *   <li>这里守「哪些格式收、多大算超限、内容是不是真的图片」—— 它们是策略，会随需求变；</li>
 *   <li>{@code TaskAttachment} 守「这是一张图片」这类结构性不变量；</li>
 *   <li>数据库守外键（附件必须挂在真实任务上）。</li>
 * </ol>
 *
 * <p>为什么不收其它文件类型：这个入口是给「有些任务光靠文字说不清」用的（截图、设计稿），
 * 收进来就要考虑下载、预览、隔离，成本与收益不成比例。SVG 也在拒收之列 ——
 * 它是能带脚本的 XML，服务端原样回吐就是一个 XSS 入口。
 */
public class TaskAttachmentService {

    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository attachmentRepository;
    private final AttachmentStorage storage;
    private final long maxBytes;

    public TaskAttachmentService(TaskRepository taskRepository,
                                 TaskAttachmentRepository attachmentRepository,
                                 AttachmentStorage storage,
                                 long maxBytes) {
        this.taskRepository = taskRepository;
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.maxBytes = maxBytes;
    }

    public TaskAttachment attach(TaskId taskId, UploadedAttachment file) {
        return attach(taskId, null, file);
    }

    public TaskAttachment attach(TaskId taskId, UserId ownerId, UploadedAttachment file) {
        requireTask(taskId, ownerId);
        ImageUploadPolicy.ValidatedImage image = ImageUploadPolicy.validate(
                file.filename(), file.contentType(), file.content(), maxBytes);
        String storagePath = storage.store(AttachmentScope.TASK, taskId.value(), image.extension(), image.content());
        return attachmentRepository.save(TaskAttachment.of(
                taskId, image.filename(), image.contentType(), image.sizeBytes(), storagePath));
    }

    public List<TaskAttachment> list(TaskId taskId) {
        return list(taskId, null);
    }

    public List<TaskAttachment> list(TaskId taskId, UserId ownerId) {
        requireTask(taskId, ownerId);
        return attachmentRepository.findByTask(taskId);
    }

    public TaskAttachment load(AttachmentId id) {
        return load(id, null);
    }

    public TaskAttachment load(AttachmentId id, UserId ownerId) {
        return (ownerId == null
                ? attachmentRepository.findById(id)
                : attachmentRepository.findByIdOwned(id, ownerId))
                .orElseThrow(() -> new EntityNotFoundException("附件不存在：" + id));
    }

    /** 读回图片本体，供看图接口用。 */
    public byte[] content(TaskAttachment attachment) {
        return storage.read(attachment.storagePath());
    }

    private void requireTask(TaskId taskId, UserId ownerId) {
        (ownerId == null ? taskRepository.findById(taskId) : taskRepository.findByIdOwned(taskId, ownerId))
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
    }

}
