package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.DeliveryStage;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.model.RequirementDesignAttachment;
import com.pydlove.regent.delivery.domain.model.RequirementDesignAttachmentId;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModule;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModuleId;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignAttachmentRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignModuleRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.kernel.AttachmentScope;
import com.pydlove.regent.shared.kernel.DocumentUploadPolicy;
import com.pydlove.regent.shared.kernel.ImageUploadPolicy;

import java.util.List;
import java.util.Locale;

/** 概要设计与模块详细设计附件上传、列表与下载。 */
public class RequirementDesignAttachmentService {

    private final RequirementDeliveryRepository deliveryRepository;
    private final RequirementDesignModuleRepository moduleRepository;
    private final RequirementDesignAttachmentRepository attachmentRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final ProjectMemberAccessService memberAccessService;
    private final AttachmentStorage storage;
    private final long maxBytes;

    public RequirementDesignAttachmentService(
            RequirementDeliveryRepository deliveryRepository,
            RequirementDesignModuleRepository moduleRepository,
            RequirementDesignAttachmentRepository attachmentRepository,
            ProjectProfileRepository projectProfileRepository,
            ProjectMemberAccessService memberAccessService,
            AttachmentStorage storage,
            long maxBytes) {
        this.deliveryRepository = deliveryRepository;
        this.moduleRepository = moduleRepository;
        this.attachmentRepository = attachmentRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.memberAccessService = memberAccessService;
        this.storage = storage;
        this.maxBytes = maxBytes;
    }

    public RequirementDesignAttachment uploadOutline(
            RequirementDeliveryId deliveryId,
            UserId actorId,
            UploadedRequirementDesignAttachment upload) {
        RequirementDelivery delivery = loadDelivery(deliveryId, actorId);
        if (delivery.getStage() != DeliveryStage.OUTLINE_DESIGN) {
            throw new DomainException("只有概要设计阶段可以上传概要设计文档");
        }
        memberAccessService.requireRole(
                delivery.getProjectId(), actorId, ProjectMemberRole.TECH_LEAD);
        return store(deliveryId, null, actorId, upload);
    }

    public RequirementDesignAttachment uploadModule(
            RequirementDeliveryId deliveryId,
            RequirementDesignModuleId moduleId,
            UserId actorId,
            UploadedRequirementDesignAttachment upload) {
        RequirementDelivery delivery = loadDelivery(deliveryId, actorId);
        if (delivery.getStage() != DeliveryStage.DESIGN) {
            throw new DomainException("只有详细设计阶段可以上传模块设计文档");
        }
        RequirementDesignModule module = moduleRepository
                .findByIdAndDelivery(moduleId, deliveryId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "设计模块不存在：" + moduleId.value()));
        if (!module.getAssigneeId().equals(actorId)) {
            memberAccessService.requireRole(
                    delivery.getProjectId(), actorId, ProjectMemberRole.TECH_LEAD);
        }
        module.requireEditable();
        return store(deliveryId, moduleId, actorId, upload);
    }

    public List<RequirementDesignAttachment> listOutline(
            RequirementDeliveryId deliveryId, UserId actorId) {
        loadDelivery(deliveryId, actorId);
        return attachmentRepository.findByDelivery(deliveryId).stream()
                .filter(RequirementDesignAttachment::outline)
                .toList();
    }

    public List<RequirementDesignAttachment> listModule(
            RequirementDeliveryId deliveryId,
            RequirementDesignModuleId moduleId,
            UserId actorId) {
        loadDelivery(deliveryId, actorId);
        return attachmentRepository.findByModule(moduleId);
    }

    public RequirementDesignAttachment load(
            RequirementDesignAttachmentId id,
            RequirementDeliveryId deliveryId,
            UserId actorId) {
        loadDelivery(deliveryId, actorId);
        RequirementDesignAttachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "设计附件不存在：" + id.value()));
        if (!attachment.deliveryId().equals(deliveryId)) {
            throw new EntityNotFoundException("设计附件不属于当前需求交付");
        }
        return attachment;
    }

    public byte[] content(RequirementDesignAttachment attachment) {
        return storage.read(attachment.storagePath());
    }

    private RequirementDesignAttachment store(
            RequirementDeliveryId deliveryId,
            RequirementDesignModuleId moduleId,
            UserId actorId,
            UploadedRequirementDesignAttachment upload) {
        ValidatedAttachment validated = validate(upload);
        String storagePath = storage.store(
                AttachmentScope.DELIVERY_DESIGN,
                moduleId == null ? deliveryId.value() : moduleId.value(),
                validated.extension(),
                validated.content());
        return attachmentRepository.save(RequirementDesignAttachment.create(
                deliveryId,
                moduleId,
                validated.filename(),
                validated.contentType(),
                validated.sizeBytes(),
                storagePath,
                actorId));
    }

    private RequirementDelivery loadDelivery(
            RequirementDeliveryId deliveryId, UserId actorId) {
        RequirementDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "需求交付不存在：" + deliveryId.value()));
        projectProfileRepository.findById(delivery.getProjectId(), actorId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "项目不存在：" + delivery.getProjectId().value()));
        return delivery;
    }

    private ValidatedAttachment validate(UploadedRequirementDesignAttachment upload) {
        if (upload == null) {
            throw new DomainException("没有收到设计附件");
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
