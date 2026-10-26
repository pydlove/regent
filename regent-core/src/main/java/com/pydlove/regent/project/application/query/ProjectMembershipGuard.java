package com.pydlove.regent.project.application.query;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.application.query.OrganizationAccessService;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.ForbiddenException;

/** 项目成员的读取与管理权限：项目内 OWNER，或所属组织的管理者。 */
public class ProjectMembershipGuard {

    private final ProjectMemberRepository memberRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final OrganizationAccessService organizationAccessService;

    public ProjectMembershipGuard(ProjectMemberRepository memberRepository,
                                  ProjectProfileRepository projectProfileRepository,
                                  OrganizationAccessService organizationAccessService) {
        this.memberRepository = memberRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.organizationAccessService = organizationAccessService;
    }

    public ProjectProfile requireReadable(ProjectId projectId, UserId actorId) {
        ProjectProfile readable = projectProfileRepository.findById(projectId, actorId)
                .orElse(null);
        if (readable != null) return readable;
        ProjectProfile project = requireProject(projectId);
        if (isOrganizationManager(project, actorId)) return project;
        throw new ForbiddenException("你没有该项目的访问权限");
    }

    public ProjectProfile requireManage(ProjectId projectId, UserId actorId) {
        ProjectProfile project = requireProject(projectId);
        if (isProjectOwner(project, actorId) || isOrganizationManager(project, actorId)) {
            return project;
        }
        throw new ForbiddenException("只有项目负责人、组织管理员或项目经理可以管理项目成员");
    }

    private boolean isProjectOwner(ProjectProfile project, UserId actorId) {
        if (actorId.equals(project.ownerId())) return true;
        return memberRepository.findRoles(project.id(), actorId)
                .contains(ProjectMemberRole.OWNER);
    }

    private boolean isOrganizationManager(ProjectProfile project, UserId actorId) {
        if (project.organizationId() == null) return false;
        return organizationAccessService.roles(project.organizationId(), actorId).stream()
                .anyMatch(OrganizationRole::canManageProjects);
    }

    private ProjectProfile requireProject(ProjectId projectId) {
        return projectProfileRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId));
    }
}
