package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.model.ProductVersionStatus;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.project.domain.repository.WorkflowTemplateRepository;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.time.Instant;

/** 产品版本规划命令。 */
public class ProductVersionCommandService {

    private final ProductVersionRepository versionRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final ProjectMemberAccessService memberAccessService;
    private final InitiativeRepository initiativeRepository;
    private final RequirementRepository requirementRepository;
    private final WorkflowTemplateRepository workflowTemplateRepository;
    private final TransactionRunner transactionRunner;

    public ProductVersionCommandService(ProductVersionRepository versionRepository,
                                        ProjectProfileRepository projectProfileRepository,
                                        ProjectMemberAccessService memberAccessService,
                                        InitiativeRepository initiativeRepository,
                                        RequirementRepository requirementRepository) {
        this(versionRepository, projectProfileRepository, memberAccessService,
                initiativeRepository, requirementRepository, null, null);
    }

    public ProductVersionCommandService(ProductVersionRepository versionRepository,
                                        ProjectProfileRepository projectProfileRepository,
                                        ProjectMemberAccessService memberAccessService,
                                        InitiativeRepository initiativeRepository,
                                        RequirementRepository requirementRepository,
                                        WorkflowTemplateRepository workflowTemplateRepository) {
        this(versionRepository, projectProfileRepository, memberAccessService,
                initiativeRepository, requirementRepository, workflowTemplateRepository, null);
    }

    public ProductVersionCommandService(ProductVersionRepository versionRepository,
                                        ProjectProfileRepository projectProfileRepository,
                                        ProjectMemberAccessService memberAccessService,
                                        InitiativeRepository initiativeRepository,
                                        RequirementRepository requirementRepository,
                                        WorkflowTemplateRepository workflowTemplateRepository,
                                        TransactionRunner transactionRunner) {
        this.versionRepository = versionRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.memberAccessService = memberAccessService;
        this.initiativeRepository = initiativeRepository;
        this.requirementRepository = requirementRepository;
        this.workflowTemplateRepository = workflowTemplateRepository;
        this.transactionRunner = transactionRunner;
    }

    public ProductVersion create(ProjectId projectId,
                                 String versionNo,
                                 String name,
                                 String description,
                                 UserId actorId) {
        requireProject(projectId, actorId);
        memberAccessService.requireRoleOrOwner(
                projectId, actorId, ProjectMemberRole.PRODUCT);
        if (versionRepository.existsByProjectAndVersionNo(projectId, versionNo)) {
            throw new DomainException("产品版本号已存在：" + versionNo);
        }
        ProductVersion version = ProductVersion.create(
                projectId,
                versionNo,
                name == null || name.isBlank() ? versionNo : name,
                description,
                actorId);
        versionRepository.save(version);
        snapshotWorkflowTemplate(version);
        return version;
    }

    public ProductVersion update(ProductVersionId id,
                                 String name,
                                 String description,
                                 UserId actorId) {
        ProductVersion version = load(id, actorId);
        memberAccessService.requireRoleOrOwner(
                version.getProjectId(), actorId, ProjectMemberRole.PRODUCT);
        version.update(
                name == null || name.isBlank() ? version.getName() : name,
                description);
        versionRepository.update(version);
        return version;
    }

    public ProductVersion close(ProductVersionId id, UserId actorId) {
        ProductVersion version = load(id, actorId);
        memberAccessService.requireRoleOrOwner(
                version.getProjectId(), actorId, ProjectMemberRole.PRODUCT);
        version.close();
        versionRepository.update(version);
        return version;
    }

    public void delete(ProductVersionId id, UserId actorId) {
        Runnable action = () -> {
            ProductVersion version = load(id, actorId);
            memberAccessService.requireRole(
                    version.getProjectId(), actorId, ProjectMemberRole.OWNER);
            if (requirementRepository.existsByVersion(id)) {
                throw new DomainException("产品版本已有需求记录，不能删除；请改为关闭版本");
            }
            boolean hasInitiative = initiativeRepository.findByVersion(id).isPresent();
            if (hasInitiative && version.getStatus() != ProductVersionStatus.CLOSED) {
                throw new DomainException("产品版本已有立项，请先关闭版本后再删除");
            }
            if (hasInitiative) {
                initiativeRepository.deleteByVersion(id);
            }
            versionRepository.delete(id);
        };
        if (transactionRunner == null) {
            action.run();
        } else {
            transactionRunner.inTransaction(action);
        }
    }

    private ProductVersion load(ProductVersionId id, UserId actorId) {
        ProductVersion version = versionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "产品版本不存在：" + id.value()));
        requireProject(version.getProjectId(), actorId);
        return version;
    }

    private void requireProject(ProjectId projectId, UserId actorId) {
        projectProfileRepository.findById(projectId, actorId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "项目不存在：" + projectId.value()));
    }

    private void snapshotWorkflowTemplate(ProductVersion version) {
        if (workflowTemplateRepository == null || version.getId() == null) return;
        var templateId = workflowTemplateRepository.findProjectTemplate(version.getProjectId())
                .orElseGet(() -> workflowTemplateRepository.findDefault()
                        .map(template -> template.id())
                        .orElse(null));
        if (templateId != null) {
            workflowTemplateRepository.bindVersion(version.getId(), templateId, Instant.now());
        }
    }
}
