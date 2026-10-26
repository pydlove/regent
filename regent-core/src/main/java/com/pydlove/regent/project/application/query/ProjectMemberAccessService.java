package com.pydlove.regent.project.application.query;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.shared.domain.DomainException;

import java.util.List;

/** 项目内角色校验。只有一个成员时允许 owner 代审，避免单人环境完全无法推进。 */
public class ProjectMemberAccessService {

    private final ProjectMemberRepository memberRepository;

    public ProjectMemberAccessService(ProjectMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public List<ProjectMemberRole> roles(ProjectId projectId, UserId userId) {
        return memberRepository.findRoles(projectId, userId);
    }

    public RoleGrant requireRole(ProjectId projectId,
                                 UserId userId,
                                 ProjectMemberRole requiredRole) {
        List<ProjectMemberRole> roles = memberRepository.findRoles(projectId, userId);
        if (roles.contains(requiredRole)) {
            return new RoleGrant(requiredRole, false);
        }
        if (roles.contains(ProjectMemberRole.OWNER)
                && memberRepository.countDistinctUsers(projectId) == 1) {
            return new RoleGrant(requiredRole, true);
        }
        throw new DomainException("当前阶段需要「" + label(requiredRole)
                + "」角色；请先在项目成员中分配该角色");
    }

    /** 项目负责人可执行管理动作；其他成员仍必须具备指定角色。 */
    public void requireRoleOrOwner(ProjectId projectId,
                                   UserId userId,
                                   ProjectMemberRole requiredRole) {
        List<ProjectMemberRole> roles = memberRepository.findRoles(projectId, userId);
        if (roles.contains(requiredRole) || roles.contains(ProjectMemberRole.OWNER)) {
            return;
        }
        throw new DomainException("当前操作需要「" + label(requiredRole)
                + "」角色或项目负责人权限");
    }

    public static String label(ProjectMemberRole role) {
        return switch (role) {
            case OWNER -> "项目负责人";
            case BUSINESS_OWNER -> "业务负责人";
            case PRODUCT -> "产品负责人";
            case TECH_LEAD -> "技术负责人";
            case ARCHITECT -> "架构师";
            case DEVELOPER -> "开发";
            case QA -> "测试负责人";
            case TESTER -> "测试人员";
            case OPS -> "运维/SRE";
            case SECURITY -> "安全/合规";
            case RELEASE_MANAGER -> "发布负责人";
            case VIEWER -> "观察者";
            case AUDITOR -> "审计员";
        };
    }

    public record RoleGrant(ProjectMemberRole role, boolean soloFallback) {
    }
}
