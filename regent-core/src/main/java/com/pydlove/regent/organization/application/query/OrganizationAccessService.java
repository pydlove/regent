package com.pydlove.regent.organization.application.query;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.organization.domain.repository.OrganizationMemberRepository;
import com.pydlove.regent.shared.domain.ForbiddenException;

import java.util.Arrays;
import java.util.List;

/** 组织级权限判断。项目角色仍然由项目成员服务负责。 */
public class OrganizationAccessService {

    private final OrganizationMemberRepository memberRepository;

    public OrganizationAccessService(OrganizationMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public List<OrganizationRole> roles(OrganizationId organizationId, UserId userId) {
        return memberRepository.findRoles(organizationId, userId);
    }

    public boolean isMember(OrganizationId organizationId, UserId userId) {
        return !roles(organizationId, userId).isEmpty();
    }

    public List<OrganizationRole> requireMember(OrganizationId organizationId, UserId userId) {
        List<OrganizationRole> roles = roles(organizationId, userId);
        if (roles.isEmpty()) {
            throw new ForbiddenException("你不是该组织成员");
        }
        return roles;
    }

    public List<OrganizationRole> requireManagerAccess(OrganizationId organizationId, UserId userId) {
        List<OrganizationRole> roles = requireMember(organizationId, userId);
        if (roles.stream().noneMatch(OrganizationRole::canManageProjects)) {
            throw new ForbiddenException("只有组织管理员或项目经理可以访问组织管理");
        }
        return roles;
    }

    public List<OrganizationRole> requireAdmin(OrganizationId organizationId, UserId userId) {
        List<OrganizationRole> roles = requireMember(organizationId, userId);
        if (roles.stream().noneMatch(OrganizationRole::canManageOrganization)) {
            throw new ForbiddenException("只有组织管理员可以执行该操作");
        }
        return roles;
    }

    public void requireAnyRole(OrganizationId organizationId,
                               UserId userId,
                               OrganizationRole... required) {
        List<OrganizationRole> roles = requireMember(organizationId, userId);
        boolean matched = Arrays.stream(required).anyMatch(roles::contains);
        if (!matched) {
            throw new ForbiddenException("当前账号没有所需组织角色");
        }
    }

    public static String label(OrganizationRole role) {
        return switch (role) {
            case ORG_OWNER -> "组织所有者";
            case ORG_ADMIN -> "组织管理员";
            case PROJECT_MANAGER -> "项目经理";
            case ORG_MEMBER -> "普通成员";
        };
    }
}
