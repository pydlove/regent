package com.pydlove.regent.project.application.command;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMember;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.project.application.query.ProjectMembershipGuard;
import com.pydlove.regent.organization.application.query.OrganizationAccessService;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 项目成员角色维护。当前只允许项目 owner 管理。 */
public class ProjectMemberCommandService {

    private final ProjectMemberRepository memberRepository;
    private final ProjectMembershipGuard membershipGuard;
    private final OrganizationAccessService organizationAccessService;
    private final UserRepository userRepository;
    private final TransactionRunner transactionRunner;

    public ProjectMemberCommandService(ProjectMemberRepository memberRepository,
                                       ProjectMembershipGuard membershipGuard,
                                       OrganizationAccessService organizationAccessService,
                                       UserRepository userRepository,
                                       TransactionRunner transactionRunner) {
        this.memberRepository = memberRepository;
        this.membershipGuard = membershipGuard;
        this.organizationAccessService = organizationAccessService;
        this.userRepository = userRepository;
        this.transactionRunner = transactionRunner;
    }

    public ProjectMemberViewResult add(ProjectId projectId,
                                       String email,
                                       ProjectMemberRole role,
                                       UserId actorId) {
        var project = membershipGuard.requireManage(projectId, actorId);
        if (role == null || role == ProjectMemberRole.OWNER) {
            throw new DomainException("不能通过成员角色接口授予 OWNER；项目负责人创建项目时自动指定");
        }
        User user = userRepository.findByEmail(Email.of(email))
                .orElseThrow(() -> new DomainException("该邮箱尚未注册 Regent，请先让成员注册账号"));
        if (project.organizationId() != null
                && !organizationAccessService.isMember(project.organizationId(), user.getId())) {
            throw new DomainException("该账号还不是项目所属组织成员，请先加入组织");
        }
        if (!memberRepository.exists(projectId, user.getId(), role)) {
            memberRepository.save(ProjectMember.assign(projectId, user.getId(), role));
        }
        return new ProjectMemberViewResult(user.getId().value(), user.getDisplayName().value(), role);
    }

    public void remove(ProjectId projectId,
                       UserId userId,
                       ProjectMemberRole role,
                       UserId actorId) {
        membershipGuard.requireManage(projectId, actorId);
        if (role == null || role == ProjectMemberRole.OWNER) {
            throw new DomainException("不能移除项目负责人的 OWNER 角色");
        }
        memberRepository.delete(projectId, userId, role);
    }

    public void updateRoles(ProjectId projectId,
                            UserId userId,
                            List<ProjectMemberRole> requestedRoles,
                            UserId actorId) {
        membershipGuard.requireManage(projectId, actorId);
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "项目成员账号不存在：" + userId.value()));
        List<ProjectMemberRole> existing = memberRepository.findRoles(projectId, userId);
        if (existing.isEmpty()) {
            throw new EntityNotFoundException("该账号还不是项目成员");
        }
        LinkedHashSet<ProjectMemberRole> normalized = new LinkedHashSet<>();
        if (requestedRoles != null) {
            requestedRoles.stream().filter(Objects::nonNull).forEach(normalized::add);
        }
        if (normalized.contains(ProjectMemberRole.OWNER)) {
            throw new DomainException("项目负责人 OWNER 角色不能通过角色调整接口修改");
        }
        boolean owner = existing.contains(ProjectMemberRole.OWNER);
        if (!owner && normalized.isEmpty()) {
            throw new DomainException("项目成员至少需要保留一个项目角色");
        }
        List<ProjectMemberRole> nextRoles = List.copyOf(normalized);
        transactionRunner.inTransaction(() -> {
            for (ProjectMemberRole role : existing) {
                if (role != ProjectMemberRole.OWNER && !nextRoles.contains(role)) {
                    memberRepository.delete(projectId, userId, role);
                }
            }
            for (ProjectMemberRole role : nextRoles) {
                memberRepository.ensure(projectId, userId, role);
            }
            return null;
        });
    }

    public record ProjectMemberViewResult(long userId, String displayName, ProjectMemberRole role) {
    }
}
