package com.pydlove.regent.organization.application.query;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.model.OrganizationMember;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.organization.domain.repository.OrganizationMemberRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrganizationAccessServiceTest {

    private final OrganizationAccessService service = new OrganizationAccessService(
            new FakeMemberRepository());

    @Test
    @DisplayName("项目经理可以进入组织管理，但不能管理组织账号")
    void projectManagerHasLimitedManagementAccess() {
        assertDoesNotThrow(() -> service.requireManagerAccess(
                OrganizationId.of(1), UserId.of(3)));
        assertThrows(DomainException.class, () -> service.requireAdmin(
                OrganizationId.of(1), UserId.of(3)));
    }

    @Test
    @DisplayName("普通成员不能进入组织管理")
    void ordinaryMemberCannotManageOrganization() {
        assertThrows(DomainException.class, () -> service.requireManagerAccess(
                OrganizationId.of(1), UserId.of(4)));
    }

    private static final class FakeMemberRepository implements OrganizationMemberRepository {

        private final List<OrganizationMember> members = List.of(
                OrganizationMember.assign(OrganizationId.of(1), UserId.of(2),
                        OrganizationRole.ORG_ADMIN),
                OrganizationMember.assign(OrganizationId.of(1), UserId.of(3),
                        OrganizationRole.PROJECT_MANAGER),
                OrganizationMember.assign(OrganizationId.of(1), UserId.of(4),
                        OrganizationRole.ORG_MEMBER));

        @Override
        public void save(OrganizationMember member) {
        }

        @Override
        public List<OrganizationMember> findByOrganization(OrganizationId organizationId) {
            return members;
        }

        @Override
        public List<OrganizationMember> findByUser(UserId userId) {
            return members.stream().filter(member -> member.userId().equals(userId)).toList();
        }

        @Override
        public List<OrganizationRole> findRoles(OrganizationId organizationId, UserId userId) {
            return members.stream()
                    .filter(member -> member.organizationId().equals(organizationId)
                            && member.userId().equals(userId))
                    .map(OrganizationMember::role)
                    .toList();
        }

        @Override
        public boolean exists(OrganizationId organizationId,
                              UserId userId,
                              OrganizationRole role) {
            return findRoles(organizationId, userId).contains(role);
        }

        @Override
        public void deleteRole(OrganizationId organizationId,
                               UserId userId,
                               OrganizationRole role) {
        }

        @Override
        public void deleteMember(OrganizationId organizationId, UserId userId) {
        }

        @Override
        public int countDistinctUsers(OrganizationId organizationId) {
            return members.size();
        }

        @Override
        public int countByRole(OrganizationId organizationId, OrganizationRole role) {
            return (int) members.stream().filter(member -> member.role() == role).count();
        }
    }
}
