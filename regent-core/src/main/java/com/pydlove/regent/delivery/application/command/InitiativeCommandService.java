package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.DeliveryGate;
import com.pydlove.regent.delivery.domain.model.GateDecision;
import com.pydlove.regent.delivery.domain.model.GateScope;
import com.pydlove.regent.delivery.domain.model.GateType;
import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.ProductVersionStatus;
import com.pydlove.regent.delivery.domain.repository.DeliveryGateRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeAttachmentRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativePlanRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

/** 立项的创建、提交与审批。 */
public class InitiativeCommandService {

    private final InitiativeRepository initiativeRepository;
    private final InitiativeAttachmentRepository attachmentRepository;
    private final DeliveryGateRepository gateRepository;
    private final ProjectMemberAccessService memberAccessService;
    private final ProjectProfileRepository projectProfileRepository;
    private final ProductVersionRepository productVersionRepository;
    private final InitiativePlanRepository planRepository;
    private final TransactionRunner transactionRunner;

    public InitiativeCommandService(InitiativeRepository initiativeRepository,
                                    InitiativeAttachmentRepository attachmentRepository,
                                    DeliveryGateRepository gateRepository,
                                    ProjectMemberAccessService memberAccessService,
                                    ProjectProfileRepository projectProfileRepository,
                                    TransactionRunner transactionRunner) {
        this(initiativeRepository, attachmentRepository, gateRepository,
                memberAccessService, projectProfileRepository, null, null, transactionRunner);
    }

    public InitiativeCommandService(InitiativeRepository initiativeRepository,
                                    InitiativeAttachmentRepository attachmentRepository,
                                    DeliveryGateRepository gateRepository,
                                    ProjectMemberAccessService memberAccessService,
                                    ProjectProfileRepository projectProfileRepository,
                                    ProductVersionRepository productVersionRepository,
                                    TransactionRunner transactionRunner) {
        this(initiativeRepository, attachmentRepository, gateRepository,
                memberAccessService, projectProfileRepository, productVersionRepository,
                null, transactionRunner);
    }

    public InitiativeCommandService(InitiativeRepository initiativeRepository,
                                    InitiativeAttachmentRepository attachmentRepository,
                                    DeliveryGateRepository gateRepository,
                                    ProjectMemberAccessService memberAccessService,
                                    ProjectProfileRepository projectProfileRepository,
                                    ProductVersionRepository productVersionRepository,
                                    InitiativePlanRepository planRepository,
                                    TransactionRunner transactionRunner) {
        this.initiativeRepository = initiativeRepository;
        this.attachmentRepository = attachmentRepository;
        this.gateRepository = gateRepository;
        this.memberAccessService = memberAccessService;
        this.projectProfileRepository = projectProfileRepository;
        this.productVersionRepository = productVersionRepository;
        this.planRepository = planRepository;
        this.transactionRunner = transactionRunner;
    }

    public Initiative create(SaveInitiativeCommand command, UserId ownerId) {
        requireProject(command.projectId(), ownerId);
        memberAccessService.requireRole(
                command.projectId(), ownerId, ProjectMemberRole.PRODUCT);
        ProductVersion version = requirePlanningVersion(command);
        String title = version == null
                ? command.title()
                : textOrFallback(command.title(), version.getVersionNo() + " 版本立项");
        String businessGoal = version == null
                ? command.businessGoal()
                : textOrFallback(
                        command.businessGoal(),
                        textOrFallback(version.getDescription(),
                                "版本 " + version.getVersionNo() + " 的研发立项"));
        Initiative initiative = version == null
                ? Initiative.create(
                        command.projectId(),
                        ownerId,
                        title,
                        businessGoal,
                        command.scopeText(),
                        command.outOfScopeText(),
                        command.successMetrics(),
                        command.stakeholders(),
                        command.targetVersion(),
                        command.riskText())
                : Initiative.create(
                        command.projectId(),
                        version.getId(),
                        ownerId,
                        title,
                        businessGoal,
                        command.scopeText(),
                        command.outOfScopeText(),
                        command.successMetrics(),
                        command.stakeholders(),
                        version.getVersionNo(),
                        command.riskText());
        if (command.meetingMinutes() != null) {
            initiative.updateMeetingMinutes(command.meetingMinutes());
        }
        initiativeRepository.save(initiative);
        return initiative;
    }

    public Initiative update(InitiativeId id, SaveInitiativeCommand command, UserId ownerId) {
        Initiative initiative = load(id, ownerId);
        memberAccessService.requireRole(
                initiative.getProjectId(), ownerId, ProjectMemberRole.PRODUCT);
        if (!initiative.getProjectId().equals(command.projectId())) {
            throw new com.pydlove.regent.shared.domain.DomainException("立项创建后不能更换归属项目");
        }
        if (command.versionId() != null
                && !command.versionId().equals(initiative.getVersionId())) {
            throw new com.pydlove.regent.shared.domain.DomainException("立项创建后不能更换产品版本");
        }
        initiative.update(
                textOrFallback(command.title(), initiative.getTitle()),
                textOrFallback(command.businessGoal(), initiative.getBusinessGoal()),
                textOrFallback(command.scopeText(), initiative.getScopeText()),
                textOrFallback(command.outOfScopeText(), initiative.getOutOfScopeText()),
                textOrFallback(command.successMetrics(), initiative.getSuccessMetrics()),
                textOrFallback(command.stakeholders(), initiative.getStakeholders()),
                initiative.getVersionId() == null
                        ? command.targetVersion()
                        : initiative.getTargetVersion(),
                textOrFallback(command.riskText(), initiative.getRiskText()));
        if (command.meetingMinutes() != null) {
            initiative.updateMeetingMinutes(command.meetingMinutes());
        }
        initiativeRepository.save(initiative);
        return initiative;
    }

    public Initiative submit(InitiativeId id, UserId ownerId) {
        Initiative initiative = load(id, ownerId);
        memberAccessService.requireRole(
                initiative.getProjectId(), ownerId, ProjectMemberRole.PRODUCT);
        if (initiative.getVersionId() != null
                && (initiative.getMeetingMinutes() == null
                        || initiative.getMeetingMinutes().isBlank())
                && attachmentRepository.findByInitiative(id).isEmpty()) {
            throw new DomainException("请填写会议纪要或上传会议纪要附件后再提交审批");
        }
        if (initiative.getVersionId() != null
                && planRepository != null
                && planRepository.findByInitiative(id).isEmpty()) {
            throw new DomainException("请先由项目负责人配置版本计划、排期和工作量后再提交审批");
        }
        initiative.submit();
        initiativeRepository.save(initiative);
        return initiative;
    }

    public Initiative approve(InitiativeId id,
                              UserId ownerId,
                              String comment) {
        return transactionRunner.inTransaction(() -> {
            Initiative initiative = load(id, ownerId);
            var grant = memberAccessService.requireRole(
                    initiative.getProjectId(), ownerId, ProjectMemberRole.OWNER);
            initiative.approve();
            initiativeRepository.save(initiative);
            gateRepository.save(DeliveryGate.record(
                    GateScope.INITIATIVE,
                    initiative.getId().value(),
                    GateType.INITIATIVE_APPROVAL,
                    GateDecision.PASSED,
                    grant.role().name(),
                    withFallback(comment, grant.soloFallback()),
                    ownerId));
            return initiative;
        });
    }

    public Initiative reject(InitiativeId id,
                             UserId ownerId,
                             String comment) {
        return transactionRunner.inTransaction(() -> {
            Initiative initiative = load(id, ownerId);
            var grant = memberAccessService.requireRole(
                    initiative.getProjectId(), ownerId, ProjectMemberRole.OWNER);
            if (comment == null || comment.isBlank()) {
                throw new DomainException("驳回立项时请填写审批意见");
            }
            initiative.reject();
            initiativeRepository.save(initiative);
            gateRepository.save(DeliveryGate.record(
                    GateScope.INITIATIVE,
                    initiative.getId().value(),
                    GateType.INITIATIVE_APPROVAL,
                    GateDecision.REJECTED,
                    grant.role().name(),
                    withFallback(comment, grant.soloFallback()),
                    ownerId));
            return initiative;
        });
    }

    public Initiative cancel(InitiativeId id, UserId ownerId) {
        Initiative initiative = load(id, ownerId);
        initiative.cancel();
        initiativeRepository.save(initiative);
        return initiative;
    }

    private ProductVersion requirePlanningVersion(SaveInitiativeCommand command) {
        if (command.versionId() == null) return null;
        if (productVersionRepository == null) {
            throw new DomainException("当前环境未启用版本立项");
        }
        ProductVersion version = productVersionRepository.findById(command.versionId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "产品版本不存在：" + command.versionId().value()));
        if (!version.getProjectId().equals(command.projectId())) {
            throw new DomainException("立项与产品版本不属于同一个项目");
        }
        if (version.getStatus() != ProductVersionStatus.PLANNING) {
            throw new DomainException("只有规划中的产品版本可以发起版本立项");
        }
        if (initiativeRepository.findByVersion(version.getId()).isPresent()) {
            throw new DomainException("该产品版本已经立过项");
        }
        return version;
    }

    private Initiative load(InitiativeId id, UserId ownerId) {
        Initiative initiative = initiativeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("立项不存在：" + id.value()));
        requireProject(initiative.getProjectId(), ownerId);
        return initiative;
    }

    private void requireProject(com.pydlove.regent.project.domain.model.ProjectId projectId,
                                UserId ownerId) {
        projectProfileRepository.findById(projectId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId));
    }

    private static String withFallback(String comment, boolean soloFallback) {
        if (!soloFallback) return comment;
        return (comment == null || comment.isBlank() ? "" : comment.trim() + "；")
                + "单人模式代审";
    }

    private static String textOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
