package com.pydlove.regent.requirement.application.command;

import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.model.ProductVersionStatus;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.requirement.application.query.RequirementReviewRecordView;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementPoolStatus;
import com.pydlove.regent.requirement.domain.model.RequirementReviewAttachment;
import com.pydlove.regent.requirement.domain.model.RequirementReviewAttachmentId;
import com.pydlove.regent.requirement.domain.model.RequirementReviewCandidate;
import com.pydlove.regent.requirement.domain.model.RequirementReviewRecord;
import com.pydlove.regent.requirement.domain.model.RequirementReviewRecordId;
import com.pydlove.regent.requirement.domain.model.RequirementReviewStatus;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.requirement.domain.repository.RequirementReviewAttachmentRepository;
import com.pydlove.regent.requirement.domain.repository.RequirementReviewRecordRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.kernel.AttachmentScope;
import com.pydlove.regent.shared.kernel.DocumentUploadPolicy;
import com.pydlove.regent.shared.kernel.ImageUploadPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 需求评审：产品负责人提交材料，项目负责人审核后决定是否正式化。 */
public class RequirementReviewRecordService {

    private final RequirementRepository requirementRepository;
    private final RequirementReviewRecordRepository recordRepository;
    private final RequirementReviewAttachmentRepository attachmentRepository;
    private final ProductVersionRepository versionRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final ProjectMemberAccessService memberAccessService;
    private final RequirementCommandService requirementCommandService;
    private final AttachmentStorage storage;
    private final TransactionRunner transactionRunner;
    private final long maxAttachmentBytes;

    public RequirementReviewRecordService(
            RequirementRepository requirementRepository,
            RequirementReviewRecordRepository recordRepository,
            RequirementReviewAttachmentRepository attachmentRepository,
            ProductVersionRepository versionRepository,
            ProjectProfileRepository projectProfileRepository,
            ProjectMemberAccessService memberAccessService,
            RequirementCommandService requirementCommandService,
            AttachmentStorage storage,
            TransactionRunner transactionRunner,
            long maxAttachmentBytes) {
        this.requirementRepository = requirementRepository;
        this.recordRepository = recordRepository;
        this.attachmentRepository = attachmentRepository;
        this.versionRepository = versionRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.memberAccessService = memberAccessService;
        this.requirementCommandService = requirementCommandService;
        this.storage = storage;
        this.transactionRunner = transactionRunner;
        this.maxAttachmentBytes = maxAttachmentBytes;
    }

    public RequirementReviewRecordView submit(
            ProductVersionId versionId,
            UserId actorId,
            SaveRequirementReviewCommand command,
            List<UploadedRequirementReviewAttachment> uploads) {
        return transactionRunner.inTransaction(() -> {
            ProductVersion version = requireVersion(versionId);
            requireProjectAccess(version, actorId);
            memberAccessService.requireRole(
                    version.getProjectId(), actorId, ProjectMemberRole.PRODUCT);
            if (version.getStatus() == ProductVersionStatus.CLOSED) {
                throw new DomainException("已关闭的产品版本不能提交需求评审");
            }
            if (recordRepository.findByVersion(versionId).stream()
                    .anyMatch(record -> record.getStatus() == RequirementReviewStatus.SUBMITTED)) {
                throw new DomainException("当前版本已有待审核的需求评审");
            }

            List<Requirement> candidates = requirementRepository
                    .findByProject(version.getProjectId()).stream()
                    .filter(requirement -> versionId.equals(requirement.getVersionId()))
                    .filter(requirement -> requirement.getPoolStatus() == RequirementPoolStatus.POOL
                            || requirement.getPoolStatus() == RequirementPoolStatus.REJECTED)
                    .sorted(Comparator.comparing(Requirement::getRequirementNo))
                    .toList();
            if (candidates.isEmpty()) {
                throw new DomainException("当前版本没有待评审需求");
            }
            if ((command.minutes() == null || command.minutes().isBlank())
                    && (uploads == null || uploads.isEmpty())) {
                throw new DomainException("请填写会议纪要或至少上传一份纪要文件");
            }

            List<ValidatedAttachment> validated = validateAttachments(uploads);
            List<String> storedPaths = new ArrayList<>();
            try {
                RequirementReviewRecord record = RequirementReviewRecord.submit(
                        versionId,
                        recordRepository.nextReviewNo(versionId),
                        command.reviewDate(),
                        command.minutes(),
                        candidates.stream()
                                .map(requirement -> new RequirementReviewCandidate(
                                        requirement.getId(),
                                        requirement.getRequirementNo(),
                                        requirement.getTitle()))
                                .toList(),
                        actorId);
                recordRepository.save(record);

                List<RequirementReviewAttachment> attachments = new ArrayList<>();
                for (ValidatedAttachment item : validated) {
                    String path = storage.store(
                            AttachmentScope.REQUIREMENT_REVIEW,
                            versionId.value(),
                            item.extension(),
                            item.content());
                    storedPaths.add(path);
                    attachments.add(attachmentRepository.save(
                            RequirementReviewAttachment.create(
                                    record.getId(),
                                    item.filename(),
                                    item.contentType(),
                                    item.sizeBytes(),
                                    path,
                                    actorId)));
                }

                for (Requirement requirement : candidates) {
                    requirement.submitPoolReview();
                    requirementRepository.save(requirement);
                }
                return new RequirementReviewRecordView(record, attachments);
            } catch (RuntimeException exception) {
                for (String path : storedPaths) {
                    try {
                        storage.delete(path);
                    } catch (RuntimeException ignored) {
                        // 数据库事务会回滚；单个临时文件清理失败不覆盖原始业务异常。
                    }
                }
                throw exception;
            }
        });
    }

    public RequirementReviewRecordView decide(RequirementReviewRecordId recordId,
                                              UserId actorId,
                                              RequirementReviewDecision decision,
                                              String comment) {
        return transactionRunner.inTransaction(() -> {
            RequirementReviewRecord record = recordRepository.findById(recordId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "需求评审记录不存在：" + recordId.value()));
            ProductVersion version = requireVersion(record.getVersionId());
            requireProjectAccess(version, actorId);
            memberAccessService.requireRole(
                    version.getProjectId(), actorId, ProjectMemberRole.OWNER);
            List<Requirement> requirements = record.getRequirements().stream()
                    .map(candidate -> requirementRepository.findById(candidate.requirementId())
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "评审需求不存在：" + candidate.requirementId().value())))
                    .toList();
            String reviewComment = comment == null ? null : comment.trim();
            if (decision == RequirementReviewDecision.APPROVE) {
                record.approve(actorId, reviewComment);
                recordRepository.update(record);
                for (Requirement requirement : requirements) {
                    requirementCommandService.approveOfflineReview(
                            requirement.getId(),
                            actorId,
                            reviewComment == null || reviewComment.isBlank()
                                    ? "需求评审通过"
                                    : reviewComment);
                }
            } else if (decision == RequirementReviewDecision.REJECT) {
                record.reject(actorId, reviewComment);
                recordRepository.update(record);
                for (Requirement requirement : requirements) {
                    requirementCommandService.rejectPoolReview(
                            requirement.getId(), actorId, reviewComment);
                }
            } else {
                throw new DomainException("不支持的需求评审审核结论");
            }
            return view(record);
        });
    }

    public List<RequirementReviewRecordView> list(ProductVersionId versionId, UserId actorId) {
        ProductVersion version = requireVersion(versionId);
        requireProjectAccess(version, actorId);
        return recordRepository.findByVersion(versionId).stream()
                .map(this::view)
                .toList();
    }

    public RequirementReviewAttachment loadAttachment(
            RequirementReviewAttachmentId id, UserId actorId) {
        RequirementReviewAttachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "需求评审附件不存在：" + id.value()));
        RequirementReviewRecord record = recordRepository.findById(attachment.recordId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "需求评审记录不存在：" + attachment.recordId().value()));
        ProductVersion version = requireVersion(record.getVersionId());
        requireProjectAccess(version, actorId);
        return attachment;
    }

    public byte[] content(RequirementReviewAttachment attachment) {
        return storage.read(attachment.storagePath());
    }

    private RequirementReviewRecordView view(RequirementReviewRecord record) {
        return new RequirementReviewRecordView(
                record,
                attachmentRepository.findByRecord(record.getId()));
    }

    private ProductVersion requireVersion(ProductVersionId versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "产品版本不存在：" + versionId.value()));
    }

    private void requireProjectAccess(ProductVersion version, UserId actorId) {
        projectProfileRepository.findById(version.getProjectId(), actorId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "项目不存在：" + version.getProjectId().value()));
    }

    private List<ValidatedAttachment> validateAttachments(
            List<UploadedRequirementReviewAttachment> uploads) {
        if (uploads == null || uploads.isEmpty()) return List.of();
        return uploads.stream().map(this::validate).toList();
    }

    private ValidatedAttachment validate(UploadedRequirementReviewAttachment upload) {
        if (upload == null) {
            throw new DomainException("没有收到需求评审附件");
        }
        String filename = upload.filename() == null ? "" : upload.filename();
        String lower = filename.toLowerCase(Locale.ROOT);
        String contentType = upload.contentType() == null
                ? ""
                : upload.contentType().toLowerCase(Locale.ROOT);
        byte[] content = upload.content() == null ? new byte[0] : upload.content();
        if (isImage(lower, contentType)) {
            ImageUploadPolicy.ValidatedImage image = ImageUploadPolicy.validate(
                    filename, upload.contentType(), content, maxAttachmentBytes);
            return new ValidatedAttachment(
                    image.filename(), image.contentType(), image.extension(),
                    image.sizeBytes(), image.content());
        }
        DocumentUploadPolicy.ValidatedDocument document = DocumentUploadPolicy.validate(
                filename, content.length, content, maxAttachmentBytes);
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
