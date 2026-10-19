package com.pydlove.regent.bug.application.command;

import com.pydlove.regent.bug.domain.model.BugAttachment;
import com.pydlove.regent.bug.domain.model.BugAttachmentId;
import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.bug.domain.repository.BugAttachmentRepository;
import com.pydlove.regent.bug.domain.repository.BugRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.kernel.AttachmentScope;
import com.pydlove.regent.shared.kernel.ImageUploadPolicy;

import java.util.List;

/** Bug 图片附件用例：上传、列出、读回内容。 */
public class BugAttachmentService {

    private final BugRepository bugRepository;
    private final BugAttachmentRepository attachmentRepository;
    private final AttachmentStorage storage;
    private final long maxBytes;

    public BugAttachmentService(BugRepository bugRepository,
                                BugAttachmentRepository attachmentRepository,
                                AttachmentStorage storage,
                                long maxBytes) {
        this.bugRepository = bugRepository;
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.maxBytes = maxBytes;
    }

    public BugAttachment attach(BugId bugId, UploadedBugAttachment file) {
        return attach(bugId, null, file);
    }

    public BugAttachment attach(BugId bugId, UserId ownerId, UploadedBugAttachment file) {
        requireBug(bugId, ownerId);
        ImageUploadPolicy.ValidatedImage image = ImageUploadPolicy.validate(
                file.filename(), file.contentType(), file.content(), maxBytes);
        String storagePath = storage.store(AttachmentScope.BUG, bugId.value(), image.extension(), image.content());
        return attachmentRepository.save(BugAttachment.of(
                bugId, image.filename(), image.contentType(), image.sizeBytes(), storagePath));
    }

    public List<BugAttachment> list(BugId bugId) {
        return list(bugId, null);
    }

    public List<BugAttachment> list(BugId bugId, UserId ownerId) {
        requireBug(bugId, ownerId);
        return attachmentRepository.findByBug(bugId);
    }

    public BugAttachment load(BugAttachmentId id) {
        return load(id, null);
    }

    public BugAttachment load(BugAttachmentId id, UserId ownerId) {
        return (ownerId == null
                ? attachmentRepository.findById(id)
                : attachmentRepository.findByIdOwned(id, ownerId))
                .orElseThrow(() -> new EntityNotFoundException("Bug 附件不存在：" + id));
    }

    public byte[] content(BugAttachment attachment) {
        return storage.read(attachment.storagePath());
    }

    private void requireBug(BugId bugId, UserId ownerId) {
        (ownerId == null ? bugRepository.findById(bugId) : bugRepository.findByIdOwned(bugId, ownerId))
                .orElseThrow(() -> new EntityNotFoundException("缺陷单不存在：" + bugId));
    }
}
