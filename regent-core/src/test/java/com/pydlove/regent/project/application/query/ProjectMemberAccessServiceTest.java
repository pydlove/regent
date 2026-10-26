package com.pydlove.regent.project.application.query;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMember;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMemberAccessServiceTest {

    @Test
    @DisplayName("单人项目允许 owner 代审")
    void 单人项目允许负责人代审() {
        ProjectMemberAccessService service = new ProjectMemberAccessService(
                new FakeRepository(List.of(ProjectMemberRole.OWNER), 1));

        var grant = service.requireRole(
                ProjectId.of(1), UserId.of(9), ProjectMemberRole.QA);

        assertEquals(ProjectMemberRole.QA, grant.role());
        assertTrue(grant.soloFallback());
    }

    @Test
    @DisplayName("多人项目必须拥有对应角色")
    void 多人项目必须拥有对应角色() {
        ProjectMemberAccessService service = new ProjectMemberAccessService(
                new FakeRepository(List.of(ProjectMemberRole.OWNER), 2));

        assertThrows(DomainException.class, () -> service.requireRole(
                ProjectId.of(1), UserId.of(9), ProjectMemberRole.QA));
    }

    @Test
    @DisplayName("多人项目负责人可以执行管理动作")
    void 多人项目负责人可以执行管理动作() {
        ProjectMemberAccessService service = new ProjectMemberAccessService(
                new FakeRepository(List.of(ProjectMemberRole.OWNER), 3));

        service.requireRoleOrOwner(
                ProjectId.of(1), UserId.of(9), ProjectMemberRole.PRODUCT);
    }

    @Test
    @DisplayName("普通成员不能借管理动作绕过角色")
    void 普通成员不能借管理动作绕过角色() {
        ProjectMemberAccessService service = new ProjectMemberAccessService(
                new FakeRepository(List.of(ProjectMemberRole.DEVELOPER), 3));

        assertThrows(DomainException.class, () -> service.requireRoleOrOwner(
                ProjectId.of(1), UserId.of(9), ProjectMemberRole.PRODUCT));
    }

    private record FakeRepository(
            List<ProjectMemberRole> roles,
            int memberCount) implements ProjectMemberRepository {

        @Override
        public List<ProjectMember> findByProject(ProjectId projectId) {
            return List.of();
        }

        @Override
        public List<ProjectMemberRole> findRoles(ProjectId projectId, UserId userId) {
            return roles;
        }

        @Override
        public boolean exists(ProjectId projectId, UserId userId, ProjectMemberRole role) {
            return roles.contains(role);
        }

        @Override
        public int countDistinctUsers(ProjectId projectId) {
            return memberCount;
        }

        @Override
        public ProjectMember save(ProjectMember member) {
            return member;
        }

        @Override
        public void delete(ProjectId projectId, UserId userId, ProjectMemberRole role) {
        }

        @Override
        public void ensure(ProjectId projectId, UserId userId, ProjectMemberRole role) {
        }
    }
}
