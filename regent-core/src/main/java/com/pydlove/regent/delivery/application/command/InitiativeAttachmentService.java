package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeAttachment;
import com.pydlove.regent.delivery.domain.model.InitiativeAttachmentId;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.repository.InitiativeAttachmentRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.kernel.AttachmentScope;
import com.pydlove.regent.shared.kernel.DocumentUploadPolicy;
import com.pydlove.regent.shared.kernel.ImageUploadPolicy;

import java.util.List;
import java.util.Locale;

/** 立项审批材料附件：会议纪要文档或图片。 */
public class InitiativeAttachmentService {

    private final InitiativeRepository initiativeRepository;
    private final InitiativeAttachmentRepository attachmentRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final AttachmentStorage storage;
    private final long maxBytes;

    public InitiativeAttachmentService(
            InitiativeRepository initiativeRepository,
            InitiativeAttachmentRepository attachmentRepository,
            ProjectProfileRepository projectProfileRepository,
            AttachmentStorage storage,
            long maxBytes) {
        this.initiativeRepository = initiativeRepository;
        this.attachmentRepository = attachmentRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.storage = storage;
        this.maxBytes = maxBytes;
    }

    public InitiativeAttachment attach(InitiativeId initiativeId,
                                       UserId actorId,
                                       UploadedInitiativeAttachment upload) {
        Initiative initiative = requireInitiative(initiativeId, actorId);
        initiative.requireEvidenceEditable();
        ValidatedAttachment validated = validate(upload);
        String storagePath = storage.store(
                AttachmentScope.INITIATIVE,
                initiativeId.value(),
                validated.extension(),
                validated.content());
        return attachmentRepository.save(InitiativeAttachment.create(
                initiativeId,
                validated.filename(),
                validated.contentType(),
                validated.sizeBytes(),
                storagePath,
                actorId));
    }

    public List<InitiativeAttachment> list(InitiativeId initiativeId, UserId actorId) {
        requireInitiative(initiativeId, actorId);
        return attachmentRepository.findByInitiative(initiativeId);
    }

    public InitiativeAttachment load(InitiativeAttachmentId id, UserId actorId) {
        return attachmentRepository.findByIdOwned(id, actorId)
                .orElseThrow(() -> new EntityNotFoundException("立项附件不存在：" + id.value()));
    }

    public byte[] content(InitiativeAttachment attachment) {
        return storage.read(attachment.storagePath());
    }

    private Initiative requireInitiative(InitiativeId initiativeId, UserId actorId) {
        Initiative initiative = initiativeRepository.findById(initiativeId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "立项不存在：" + initiativeId.value()));
        projectProfileRepository.findById(initiative.getProjectId(), actorId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "项目不存在：" + initiative.getProjectId().value()));
        return initiative;
    }

    private ValidatedAttachment validate(UploadedInitiativeAttachment upload) {
        if (upload == null) {
            throw new DomainException("没有收到立项附件");
        }
        String filename = upload.filename() == null ? "" : upload.filename();
        String lower = filename.toLowerCase(Locale.ROOT);
        String contentType = upload.contentType() == null
                ? ""
                : upload.contentType().toLowerCase(Locale.ROOT);
        byte[] content = upload.content() == null ? new byte[0] : upload.content();
        if (isImage(lower, contentType)) {
            ImageUploadPolicy.ValidatedImage image = ImageUploadPolicy.validate(
                    filename, upload.contentType(), content, maxBytes);
            return new ValidatedAttachment(
                    image.filename(), image.contentType(), image.extension(),
                    image.sizeBytes(), image.content());
        }
        DocumentUploadPolicy.ValidatedDocument document = DocumentUploadPolicy.validate(
                filename, content.length, content, maxBytes);
        return new ValidatedAttachment(
                document.filename(), document.contentType(), document.extension(),
                document.sizeBytes(), content);
    }

    private static boolean isImage(String filename, String contentType) {
        return contentType.startsWith("image/")
                || filename.endsWith(".png")
                || filename.endsWith(".jpg")
                || filename.endsWith(".jpeg")
                || filename.endsWith(".gif")
                || filename.endsWith(".webp");
    }

    private record ValidatedAttachment(
            String filename,
            String contentType,
            String extension,
            long sizeBytes,
            byte[] content) {
    }
}
