package com.pydlove.regent.bug.infrastructure.persistence;

import com.pydlove.regent.bug.domain.model.BugAttachment;
import com.pydlove.regent.bug.domain.model.BugAttachmentId;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.bug.domain.repository.BugAttachmentRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Bug 图片附件仓储的 MyBatis 实现。 */
@Repository
public class MyBatisBugAttachmentRepository implements BugAttachmentRepository {

    private final BugAttachmentMapper attachmentMapper;

    public MyBatisBugAttachmentRepository(BugAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    public BugAttachment save(BugAttachment attachment) {
        BugAttachmentData data = toData(attachment);
        attachmentMapper.insert(data);
        return attachment.withIdentity(BugAttachmentId.of(data.getId()));
    }

    @Override
    public Optional<BugAttachment> findById(BugAttachmentId id) {
        return Optional.ofNullable(attachmentMapper.selectById(id.value()))
                .map(MyBatisBugAttachmentRepository::toAttachment);
    }

    @Override
    public Optional<BugAttachment> findByIdOwned(BugAttachmentId id, UserId ownerId) {
        return Optional.ofNullable(attachmentMapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(MyBatisBugAttachmentRepository::toAttachment);
    }

    @Override
    public List<BugAttachment> findByBug(BugId bugId) {
        return attachmentMapper.selectByBug(bugId.value()).stream()
                .map(MyBatisBugAttachmentRepository::toAttachment)
                .toList();
    }

    private static BugAttachmentData toData(BugAttachment attachment) {
        BugAttachmentData data = new BugAttachmentData();
        data.setBugId(attachment.bugId().value());
        data.setFilename(attachment.filename());
        data.setContentType(attachment.contentType());
        data.setSizeBytes(attachment.sizeBytes());
        data.setStoragePath(attachment.storagePath());
        data.setCreatedAt(toDb(attachment.createdAt()));
        return data;
    }

    private static BugAttachment toAttachment(BugAttachmentData data) {
        return new BugAttachment(
                BugAttachmentId.of(data.getId()),
                BugId.of(data.getBugId()),
                data.getFilename(),
                data.getContentType(),
                data.getSizeBytes(),
                data.getStoragePath(),
                fromDb(data.getCreatedAt()));
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant fromDb(LocalDateTime local) {
        return local == null ? null : local.toInstant(ZoneOffset.UTC);
    }
}
