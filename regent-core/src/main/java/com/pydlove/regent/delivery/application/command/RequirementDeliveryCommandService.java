package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.DeliveryGate;
import com.pydlove.regent.delivery.domain.model.GateDecision;
import com.pydlove.regent.delivery.domain.model.GateScope;
import com.pydlove.regent.delivery.domain.model.GateType;
import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.InitiativeStatus;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.model.DesignModuleStatus;
import com.pydlove.regent.delivery.domain.model.RequirementDesignAttachment;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModule;
import com.pydlove.regent.delivery.domain.repository.DeliveryGateRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignAttachmentRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignModuleRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.requirement.domain.model.RequirementPoolStatus;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.List;

/** 需求交付单的创建与阶段推进。 */
public class RequirementDeliveryCommandService {

    private final RequirementDeliveryRepository deliveryRepository;
    private final InitiativeRepository initiativeRepository;
    private final RequirementRepository requirementRepository;
    private final DeliveryGateRepository gateRepository;
    private final ProjectMemberAccessService memberAccessService;
    private final ProjectProfileRepository projectProfileRepository;
    private final TransactionRunner transactionRunner;
    private final RequirementDesignModuleRepository moduleRepository;
    private final RequirementDesignAttachmentRepository attachmentRepository;

    public RequirementDeliveryCommandService(RequirementDeliveryRepository deliveryRepository,
                                             InitiativeRepository initiativeRepository,
                                             RequirementRepository requirementRepository,
                                             DeliveryGateRepository gateRepository,
                                             ProjectMemberAccessService memberAccessService,
                                             ProjectProfileRepository projectProfileRepository,
                                             TransactionRunner transactionRunner) {
        this(deliveryRepository, initiativeRepository, requirementRepository, gateRepository,
                memberAccessService, projectProfileRepository, transactionRunner, null, null);
    }

    public RequirementDeliveryCommandService(
            RequirementDeliveryRepository deliveryRepository,
            InitiativeRepository initiativeRepository,
            RequirementRepository requirementRepository,
            DeliveryGateRepository gateRepository,
            ProjectMemberAccessService memberAccessService,
            ProjectProfileRepository projectProfileRepository,
            TransactionRunner transactionRunner,
            RequirementDesignModuleRepository moduleRepository,
            RequirementDesignAttachmentRepository attachmentRepository) {
        this.deliveryRepository = deliveryRepository;
        this.initiativeRepository = initiativeRepository;
        this.requirementRepository = requirementRepository;
        this.gateRepository = gateRepository;
        this.memberAccessService = memberAccessService;
        this.projectProfileRepository = projectProfileRepository;
        this.transactionRunner = transactionRunner;
        this.moduleRepository = moduleRepository;
        this.attachmentRepository = attachmentRepository;
    }

    public RequirementDelivery create(InitiativeId initiativeId,
                                      RequirementId requirementId,
                                      UserId ownerId) {
        Initiative initiative = requireApprovedInitiative(initiativeId);
        ProjectId projectId = initiative.getProjectId();
        requireProject(projectId, ownerId);
        Requirement requirement = requirementRepository.findByIdOwned(requirementId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("需求不存在：" + requirementId.value()));
        RequirementDelivery delivery = createDelivery(initiative, requirement, ownerId);
        deliveryRepository.save(delivery);
        return delivery;
    }

    public RequirementDelivery createWithRequirement(
            InitiativeId initiativeId,
            CreateInitiativeRequirementCommand command,
            UserId ownerId) {
        throw new DomainException(
                "需求请先从需求池录入并完成评审，评审通过后会自动生成详细设计交付");
    }

    private RequirementDelivery createDelivery(Initiative initiative,
                                               Requirement requirement,
                                               UserId ownerId) {
        ProjectId projectId = initiative.getProjectId();
        if (!requirement.getProjectId().equals(projectId)) {
            throw new DomainException("需求与立项不属于同一个项目");
        }
        if (requirement.getInitiativeId() != null
                && !requirement.getInitiativeId().equals(initiative.getId())) {
            throw new DomainException("需求已归属其他立项，不能重复创建交付");
        }
        if (requirement.getPoolStatus() != RequirementPoolStatus.FORMAL) {
            throw new DomainException("只有评审通过的正式需求可以创建研发交付");
        }
        deliveryRepository.findActiveByRequirement(requirement.getId()).ifPresent(existing -> {
            throw new DomainException("该需求已经有进行中的研发交付");
        });
        return RequirementDelivery.create(
                initiative.getId(), projectId, requirement.getId(), ownerId);
    }

    private Initiative requireApprovedInitiative(InitiativeId initiativeId) {
        Initiative initiative = initiativeRepository.findById(initiativeId)
                .orElseThrow(() -> new EntityNotFoundException("立项不存在：" + initiativeId.value()));
        if (initiative.getStatus() != InitiativeStatus.APPROVED) {
            throw new DomainException("立项尚未批准，不能录入需求或创建交付");
        }
        return initiative;
    }

    public RequirementDelivery act(RequirementDeliveryId id,
                                   RequirementDeliveryAction action,
                                   String comment,
                                   String designSummary,
                                   String designContent,
                                   UserId ownerId) {
        return transactionRunner.inTransaction(() -> {
            RequirementDelivery delivery = load(id, ownerId);
            ProjectMemberRole requiredRole = requiredRoleFor(action);
            var grant = memberAccessService.requireRole(
                    delivery.getProjectId(), ownerId, requiredRole);
            GateType gateType;
            GateDecision gateDecision = GateDecision.PASSED;
            switch (action) {
                case APPROVE_REQUIREMENT_REVIEW -> {
                    delivery.approveRequirementReview();
                    gateType = GateType.REQUIREMENT_REVIEW;
                }
                case REJECT_REQUIREMENT_REVIEW -> {
                    delivery.rejectRequirementReview();
                    gateType = GateType.REQUIREMENT_REVIEW;
                    gateDecision = GateDecision.REJECTED;
                }
                case SUBMIT_OUTLINE_DESIGN -> {
                    boolean hasText = (designSummary != null && !designSummary.isBlank())
                            || (designContent != null && !designContent.isBlank());
                    boolean hasAttachment = attachmentRepository != null
                            && attachmentRepository.findByDelivery(delivery.getId()).stream()
                                    .anyMatch(RequirementDesignAttachment::outline);
                    if (!hasText && !hasAttachment) {
                        throw new DomainException("请填写概要设计摘要或上传概要设计文档");
                    }
                    delivery.submitOutlineDesign(designSummary, designContent, ownerId);
                    gateType = GateType.OUTLINE_DESIGN_SUBMITTED;
                }
                case SUBMIT_DESIGN -> {
                    List<RequirementDesignModule> modules = moduleRepository == null
                            ? List.of()
                            : moduleRepository.findByDelivery(delivery.getId());
                    if (modules.isEmpty()) {
                        delivery.submitDesign(designSummary, designContent);
                    } else {
                        List<String> pending = modules.stream()
                                .filter(module -> module.getStatus()
                                        != DesignModuleStatus.SUBMITTED)
                                .map(RequirementDesignModule::getName)
                                .toList();
                        if (!pending.isEmpty()) {
                            throw new DomainException(
                                    "还有模块详细设计未提交：" + String.join("、", pending));
                        }
                        delivery.submitModularDesign(designSummary, designContent);
                    }
                    gateType = GateType.DESIGN_SUBMITTED;
                }
                case APPROVE_DESIGN -> {
                    delivery.approveDesign();
                    gateType = GateType.DESIGN_REVIEW;
                }
                case REJECT_DESIGN -> {
                    delivery.rejectDesign();
                    if (moduleRepository != null) {
                        moduleRepository.reopenAll(delivery.getId());
                    }
                    gateType = GateType.DESIGN_REVIEW;
                    gateDecision = GateDecision.REJECTED;
                }
                case COMPLETE_DEVELOPMENT -> {
                    delivery.completeDevelopment();
                    gateType = GateType.DEVELOPMENT_COMPLETED;
                }
                case PASS_SMOKE -> {
                    delivery.passSmoke();
                    gateType = GateType.SMOKE_PASSED;
                }
                case PASS_AUTOMATION -> {
                    delivery.passAutomation();
                    gateType = GateType.AUTOMATION_PASSED;
                }
                case CLOSE -> {
                    delivery.close();
                    gateType = GateType.RELEASE_CLOSED;
                }
                case CANCEL -> {
                    delivery.cancel();
                    gateType = GateType.CANCELLED;
                    gateDecision = GateDecision.REJECTED;
                }
                default -> throw new DomainException("不支持的交付动作：" + action);
            }
            deliveryRepository.save(delivery);
            gateRepository.save(DeliveryGate.record(
                    GateScope.REQUIREMENT_DELIVERY,
                    delivery.getId().value(),
                    gateType,
                    gateDecision,
                    grant.role().name(),
                    withFallback(comment, grant.soloFallback()),
                    ownerId));
            return delivery;
        });
    }

    private RequirementDelivery load(RequirementDeliveryId id, UserId ownerId) {
        RequirementDelivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("需求交付不存在：" + id.value()));
        requireProject(delivery.getProjectId(), ownerId);
        return delivery;
    }

    private static ProjectMemberRole requiredRoleFor(RequirementDeliveryAction action) {
        return switch (action) {
            case APPROVE_REQUIREMENT_REVIEW, REJECT_REQUIREMENT_REVIEW ->
                    ProjectMemberRole.PRODUCT;
            case SUBMIT_OUTLINE_DESIGN, SUBMIT_DESIGN, APPROVE_DESIGN, REJECT_DESIGN,
                 COMPLETE_DEVELOPMENT ->
                    ProjectMemberRole.TECH_LEAD;
            case PASS_SMOKE, PASS_AUTOMATION -> ProjectMemberRole.QA;
            case CLOSE, CANCEL -> ProjectMemberRole.OWNER;
        };
    }

    private void requireProject(ProjectId projectId, UserId ownerId) {
        projectProfileRepository.findById(projectId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId));
    }

    private static String withFallback(String comment, boolean soloFallback) {
        if (!soloFallback) return comment;
        return (comment == null || comment.isBlank() ? "" : comment.trim() + "；")
                + "单人模式代审";
    }
}
