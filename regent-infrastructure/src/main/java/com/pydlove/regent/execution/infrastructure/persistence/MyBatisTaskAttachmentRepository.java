package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.AttachmentId;
import com.pydlove.regent.execution.domain.model.TaskAttachment;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskAttachmentRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 附件仓储的 MyBatis 实现。 */
@Repository
public class MyBatisTaskAttachmentRepository implements TaskAttachmentRepository {

    private final TaskAttachmentMapper attachmentMapper;

    public MyBatisTaskAttachmentRepository(TaskAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    public TaskAttachment save(TaskAttachment attachment) {
        TaskAttachmentData data = toData(attachment);
        attachmentMapper.insert(data);
        return attachment.withIdentity(AttachmentId.of(data.getId()));
    }

    @Override
    public Optional<TaskAttachment> findById(AttachmentId id) {
        return Optional.ofNullable(attachmentMapper.selectById(id.value())).map(MyBatisTaskAttachmentRepository::toAttachment);
    }

    @Override
    public Optional<TaskAttachment> findByIdOwned(AttachmentId id, UserId ownerId) {
        return Optional.ofNullable(attachmentMapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(MyBatisTaskAttachmentRepository::toAttachment);
    }

    @Override
    public List<TaskAttachment> findByTask(TaskId taskId) {
        return attachmentMapper.selectByTask(taskId.value()).stream()
                .map(MyBatisTaskAttachmentRepository::toAttachment)
                .toList();
    }

    private static TaskAttachmentData toData(TaskAttachment attachment) {
        TaskAttachmentData data = new TaskAttachmentData();
        data.setTaskId(attachment.taskId().value());
        data.setFilename(attachment.filename());
        data.setContentType(attachment.contentType());
        data.setSizeBytes(attachment.sizeBytes());
        data.setStoragePath(attachment.storagePath());
        data.setCreatedAt(toDb(attachment.createdAt()));
        return data;
    }

    private static TaskAttachment toAttachment(TaskAttachmentData data) {
        return new TaskAttachment(
                AttachmentId.of(data.getId()),
                TaskId.of(data.getTaskId()),
                data.getFilename(),
                data.getContentType(),
                data.getSizeBytes(),
                data.getStoragePath(),
                fromDb(data.getCreatedAt()));
    }

    /** UTC 瞬时 ↔ DATETIME 字面量。DATETIME 里没有时区，约定它存的一律是 UTC。 */
    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant fromDb(LocalDateTime local) {
        return local == null ? null : local.toInstant(ZoneOffset.UTC);
    }
}
