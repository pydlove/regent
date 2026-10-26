package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.ProductVersion;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.model.InitiativeStatus;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

/** 产品版本规划命令。 */
public class ProductVersionCommandService {

    private final ProductVersionRepository versionRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final ProjectMemberAccessService memberAccessService;
    private final InitiativeRepository initiativeRepository;

    public ProductVersionCommandService(ProductVersionRepository versionRepository,
                                        ProjectProfileRepository projectProfileRepository,
                                        ProjectMemberAccessService memberAccessService) {
        this(versionRepository, projectProfileRepository, memberAccessService, null);
    }

    public ProductVersionCommandService(ProductVersionRepository versionRepository,
                                        ProjectProfileRepository projectProfileRepository,
                                        ProjectMemberAccessService memberAccessService,
                                        InitiativeRepository initiativeRepository) {
        this.versionRepository = versionRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.memberAccessService = memberAccessService;
        this.initiativeRepository = initiativeRepository;
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

    public ProductVersion confirm(ProductVersionId id, UserId actorId) {
        ProductVersion version = load(id, actorId);
        memberAccessService.requireRoleOrOwner(
                version.getProjectId(), actorId, ProjectMemberRole.PRODUCT);
        if (initiativeRepository != null
                && initiativeRepository.findByVersion(version.getId())
                        .filter(initiative -> initiative.getStatus() == InitiativeStatus.APPROVED)
                        .isEmpty()) {
            throw new DomainException("请先完成版本立项审批，再确认产品版本");
        }
        version.confirm();
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
}
