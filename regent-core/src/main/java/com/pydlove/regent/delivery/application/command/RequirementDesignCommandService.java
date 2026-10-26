package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.DeliveryStage;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModule;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModuleId;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignAttachmentRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignModuleRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.domain.model.ProjectMember;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.List;

/** 模块详细设计、研发负责人分配和模块提交。 */
public class RequirementDesignCommandService {

    private final RequirementDeliveryRepository deliveryRepository;
    private final RequirementDesignModuleRepository moduleRepository;
    private final RequirementDesignAttachmentRepository attachmentRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectMemberAccessService memberAccessService;
    private final ProjectProfileRepository projectProfileRepository;
    private final TransactionRunner transactionRunner;

    public RequirementDesignCommandService(
            RequirementDeliveryRepository deliveryRepository,
            RequirementDesignModuleRepository moduleRepository,
            RequirementDesignAttachmentRepository attachmentRepository,
            ProjectMemberRepository memberRepository,
            ProjectMemberAccessService memberAccessService,
            ProjectProfileRepository projectProfileRepository,
            TransactionRunner transactionRunner) {
        this.deliveryRepository = deliveryRepository;
        this.moduleRepository = moduleRepository;
        this.attachmentRepository = attachmentRepository;
        this.memberRepository = memberRepository;
        this.memberAccessService = memberAccessService;
        this.projectProfileRepository = projectProfileRepository;
        this.transactionRunner = transactionRunner;
    }

    public RequirementDesignModule create(
            RequirementDeliveryId deliveryId,
            SaveDesignModuleCommand command,
            UserId actorId) {
        return transactionRunner.inTransaction(() -> {
            RequirementDelivery delivery = requireDesignStage(deliveryId, actorId);
            memberAccessService.requireRole(
                    delivery.getProjectId(), actorId, ProjectMemberRole.TECH_LEAD);
            requireDeveloper(delivery, command.assigneeId());
            return moduleRepository.save(RequirementDesignModule.create(
                    deliveryId,
                    command.name(),
                    command.description(),
                    command.assigneeId(),
                    actorId));
        });
    }

    public RequirementDesignModule updateAssignment(
            RequirementDeliveryId deliveryId,
            RequirementDesignModuleId moduleId,
            SaveDesignModuleCommand command,
            UserId actorId) {
        return transactionRunner.inTransaction(() -> {
            RequirementDelivery delivery = requireDesignStage(deliveryId, actorId);
            memberAccessService.requireRole(
                    delivery.getProjectId(), actorId, ProjectMemberRole.TECH_LEAD);
            requireDeveloper(delivery, command.assigneeId());
            RequirementDesignModule module = requireModule(deliveryId, moduleId);
            module.updateAssignment(
                    command.name(), command.description(), command.assigneeId());
            return moduleRepository.save(module);
        });
    }

    public RequirementDesignModule saveDesign(
            RequirementDeliveryId deliveryId,
            RequirementDesignModuleId moduleId,
            SaveModuleDesignCommand command,
            UserId actorId) {
        return transactionRunner.inTransaction(() -> {
            RequirementDelivery delivery = requireDesignStage(deliveryId, actorId);
            RequirementDesignModule module = requireModule(deliveryId, moduleId);
            requireModuleOwnerOrTechLead(delivery, module, actorId);
            module.saveDesign(command.designSummary(), command.designContent());
            return moduleRepository.save(module);
        });
    }

    public RequirementDesignModule submitModule(
            RequirementDeliveryId deliveryId,
            RequirementDesignModuleId moduleId,
            UserId actorId) {
        return transactionRunner.inTransaction(() -> {
            RequirementDelivery delivery = requireDesignStage(deliveryId, actorId);
            RequirementDesignModule module = requireModule(deliveryId, moduleId);
            requireModuleOwnerOrTechLead(delivery, module, actorId);
            boolean hasContent = module.getDesignContent() != null
                    && !module.getDesignContent().isBlank();
            boolean hasAttachment = !attachmentRepository.findByModule(moduleId).isEmpty();
            if (!hasContent && !hasAttachment) {
                throw new DomainException("请填写模块设计内容或上传设计附件后再提交");
            }
            module.submit();
            return moduleRepository.save(module);
        });
    }

    public RequirementDesignModule reopenModule(
            RequirementDeliveryId deliveryId,
            RequirementDesignModuleId moduleId,
            UserId actorId) {
        return transactionRunner.inTransaction(() -> {
            RequirementDelivery delivery = requireDesignStage(deliveryId, actorId);
            memberAccessService.requireRole(
                    delivery.getProjectId(), actorId, ProjectMemberRole.TECH_LEAD);
            RequirementDesignModule module = requireModule(deliveryId, moduleId);
            module.reopen();
            return moduleRepository.save(module);
        });
    }

    public void deleteModule(
            RequirementDeliveryId deliveryId,
            RequirementDesignModuleId moduleId,
            UserId actorId) {
        transactionRunner.inTransaction(() -> {
            RequirementDelivery delivery = requireDesignStage(deliveryId, actorId);
            memberAccessService.requireRole(
                    delivery.getProjectId(), actorId, ProjectMemberRole.TECH_LEAD);
            requireModule(deliveryId, moduleId);
            moduleRepository.delete(moduleId);
            return null;
        });
    }

    public List<RequirementDesignModule> modules(
            RequirementDeliveryId deliveryId, UserId actorId) {
        RequirementDelivery delivery = loadDelivery(deliveryId, actorId);
        return moduleRepository.findByDelivery(delivery.getId());
    }

    private RequirementDelivery requireDesignStage(
            RequirementDeliveryId deliveryId, UserId actorId) {
        RequirementDelivery delivery = loadDelivery(deliveryId, actorId);
        if (delivery.getStage() != DeliveryStage.DESIGN) {
            throw new DomainException("只有详细设计阶段可以维护模块和派活");
        }
        return delivery;
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

    private RequirementDesignModule requireModule(
            RequirementDeliveryId deliveryId,
            RequirementDesignModuleId moduleId) {
        return moduleRepository.findByIdAndDelivery(moduleId, deliveryId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "设计模块不存在：" + moduleId.value()));
    }

    private void requireModuleOwnerOrTechLead(
            RequirementDelivery delivery,
            RequirementDesignModule module,
            UserId actorId) {
        if (module.getAssigneeId().equals(actorId)) return;
        memberAccessService.requireRole(
                delivery.getProjectId(), actorId, ProjectMemberRole.TECH_LEAD);
    }

    private void requireDeveloper(RequirementDelivery delivery, UserId assigneeId) {
        List<ProjectMember> members = memberRepository.findByProject(delivery.getProjectId());
        boolean assigned = members.stream()
                .anyMatch(member -> member.userId().equals(assigneeId));
        if (!assigned) {
            throw new DomainException("模块负责人必须是项目团队成员");
        }
        boolean developer = members.stream()
                .anyMatch(member -> member.userId().equals(assigneeId)
                        && (member.role() == ProjectMemberRole.DEVELOPER
                        || member.role() == ProjectMemberRole.TECH_LEAD));
        if (!developer) {
            throw new DomainException("模块负责人必须具备开发或技术负责人角色");
        }
    }
}
