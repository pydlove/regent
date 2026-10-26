package com.pydlove.regent.boot.organization;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.application.command.AuthService;
import com.pydlove.regent.identity.application.command.LoginCommand;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.identity.infrastructure.security.FixedTemporaryPasswordGenerator;
import com.pydlove.regent.organization.application.command.AcceptOrganizationInvitationCommand;
import com.pydlove.regent.organization.application.command.CreateOrganizationCommand;
import com.pydlove.regent.organization.application.command.CreateOrganizationAccountCommand;
import com.pydlove.regent.organization.application.command.InviteOrganizationMemberCommand;
import com.pydlove.regent.organization.application.command.OrganizationCommandService;
import com.pydlove.regent.organization.application.query.OrganizationAccessService;
import com.pydlove.regent.organization.application.query.OrganizationQueryService;
import com.pydlove.regent.organization.domain.model.Organization;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.project.application.command.ProjectRegistrationService;
import com.pydlove.regent.project.application.command.ProjectMemberCommandService;
import com.pydlove.regent.project.application.command.ProjectMemberRoleAssignment;
import com.pydlove.regent.project.application.command.RegisterProjectCommand;
import com.pydlove.regent.project.application.query.ProjectMemberQueryService;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrganizationManagementIT {

    private static final UserId OWNER = UserId.of(980_001L);
    private static final UserId MEMBER = UserId.of(980_002L);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrganizationCommandService commandService;

    @Autowired
    private OrganizationQueryService queryService;

    @Autowired
    private OrganizationAccessService accessService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRegistrationService projectRegistrationService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProjectMemberCommandService projectMemberCommandService;

    @Autowired
    private ProjectMemberQueryService projectMemberQueryService;

    @BeforeEach
    void 准备账号() {
        jdbcTemplate.update("""
                INSERT INTO app_user
                    (id, username, display_name, email, password_hash, created_at)
                VALUES
                    (?, 'org_owner_test', '组织所有者', 'org-owner@example.com', 'x', UTC_TIMESTAMP(3)),
                    (?, 'org_member_test', '组织成员', 'org-member@example.com', 'x', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """, OWNER.value(), MEMBER.value());
    }

    @Test
    @DisplayName("组织所有者可以邀请并创建账号，项目经理可管理项目但不能管理组织")
    void invitesAccountAndEnforcesOrganizationRoles(@TempDir Path workspace) {
        Organization organization = commandService.create(
                new CreateOrganizationCommand("org_test_20261023", "组织测试", "集成测试组织"),
                OWNER);

        assertEquals(1, queryService.get(organization.getId(), OWNER).memberCount());
        assertTrue(accessService.roles(organization.getId(), OWNER)
                .contains(OrganizationRole.ORG_OWNER));

        var invitation = commandService.invite(
                organization.getId(),
                new InviteOrganizationMemberCommand(
                        "new-qa@example.com", OrganizationRole.PROJECT_MANAGER),
                OWNER);
        assertTrue(invitation.invitationUrl().contains("/invite/"));
        assertTrue(invitation.invitationUrl().endsWith(invitation.rawToken()));
        var accepted = commandService.acceptInvitation(
                invitation.rawToken(),
                new AcceptOrganizationInvitationCommand("新测试负责人", "password123"),
                null);

        assertEquals("new-qa@example.com", accepted.user().getEmail().value());
        assertTrue(userRepository.findByEmail(accepted.user().getEmail()).isPresent());
        assertTrue(accessService.roles(organization.getId(), accepted.user().getId())
                .contains(OrganizationRole.PROJECT_MANAGER));

        Project project = projectRegistrationService.register(
                new RegisterProjectCommand(
                        "pm_member_scope", "项目经理授权项目", workspace.toString(),
                        null, null, DecisionLevel.L1),
                OWNER, organization.getId());
        projectMemberCommandService.add(
                project.getId(), "org-owner@example.com", ProjectMemberRole.PRODUCT,
                accepted.user().getId());
        projectMemberCommandService.updateRoles(
                project.getId(), OWNER,
                java.util.List.of(ProjectMemberRole.TECH_LEAD, ProjectMemberRole.QA),
                accepted.user().getId());
        var ownerRoles = projectMemberQueryService.list(project.getId(), accepted.user().getId())
                .stream()
                .filter(view -> view.member().userId().equals(OWNER))
                .map(view -> view.member().role())
                .toList();
        assertTrue(ownerRoles.contains(ProjectMemberRole.OWNER));
        assertTrue(ownerRoles.contains(ProjectMemberRole.TECH_LEAD));
        assertTrue(ownerRoles.contains(ProjectMemberRole.QA));
        assertFalse(ownerRoles.contains(ProjectMemberRole.PRODUCT));
        assertThrows(DomainException.class, () -> projectMemberCommandService.updateRoles(
                project.getId(), OWNER,
                java.util.List.of(ProjectMemberRole.OWNER),
                accepted.user().getId()));
        assertTrue(projectMemberQueryService.candidates(project.getId(), accepted.user().getId())
                .stream().anyMatch(candidate -> candidate.user().getId().equals(OWNER)));
        assertTrue(queryService.members(organization.getId(), OWNER).stream()
                .filter(member -> member.user().getId().equals(OWNER))
                .flatMap(member -> member.projects().stream())
                .anyMatch(assignment -> assignment.projectId().equals(project.getId())
                        && assignment.roles().contains(ProjectMemberRole.TECH_LEAD)
                        && assignment.roles().contains(ProjectMemberRole.QA)));

        accessService.requireManagerAccess(organization.getId(), accepted.user().getId());
        assertThrows(DomainException.class, () -> accessService.requireAdmin(
                organization.getId(), accepted.user().getId()));
        assertThrows(DomainException.class, () -> commandService.invite(
                organization.getId(),
                new InviteOrganizationMemberCommand("blocked@example.com", OrganizationRole.ORG_MEMBER),
                accepted.user().getId()));

        assertTrue(queryService.members(organization.getId(), OWNER).size() >= 2);
        assertThrows(DomainException.class,
                () -> commandService.create(
                        new CreateOrganizationCommand("pm_cannot_create", "不应创建", null),
                        accepted.user().getId()));
    }

    @Test
    @DisplayName("可以批量把多个组织成员加入项目并统一分配角色")
    void batchAddsOrganizationMembersToProject(@TempDir Path workspace) {
        Organization organization = commandService.create(
                new CreateOrganizationCommand("batch_project_members", "批量成员组织", null),
                OWNER);
        commandService.addRole(
                organization.getId(), MEMBER, OrganizationRole.ORG_MEMBER, OWNER);
        Project project = projectRegistrationService.register(
                new RegisterProjectCommand(
                        "batch_project_members",
                        "批量成员项目",
                        workspace.toString(),
                        null,
                        null,
                        DecisionLevel.L1),
                OWNER,
                organization.getId());
        jdbcTemplate.update("""
                UPDATE app_user
                SET avatar_filename = 'owner.png',
                    avatar_content_type = 'image/png',
                    avatar_storage_path = '/tmp/owner.png',
                    avatar_updated_at = UTC_TIMESTAMP(3)
                WHERE id = ?
                """, OWNER.value());

        var candidate = projectMemberQueryService.candidates(project.getId(), OWNER).stream()
                .filter(item -> item.user().getId().equals(OWNER))
                .findFirst()
                .orElseThrow();
        assertNotNull(candidate.avatarVersion());

        var added = projectMemberCommandService.addAssignments(
                project.getId(),
                List.of(
                        new ProjectMemberRoleAssignment(
                                "org-owner@example.com",
                                List.of(ProjectMemberRole.QA, ProjectMemberRole.OPS)),
                        new ProjectMemberRoleAssignment(
                                "org-member@example.com",
                                List.of(ProjectMemberRole.DEVELOPER, ProjectMemberRole.VIEWER))),
                OWNER);

        assertEquals(4, added.size());
        var members = projectMemberQueryService.list(project.getId(), OWNER);
        assertTrue(members.stream().anyMatch(view ->
                view.member().userId().equals(OWNER)
                        && view.member().role() == ProjectMemberRole.QA));
        assertTrue(members.stream().anyMatch(view ->
                view.member().userId().equals(OWNER)
                        && view.member().role() == ProjectMemberRole.OPS));
        assertTrue(members.stream().anyMatch(view ->
                view.member().userId().equals(MEMBER)
                        && view.member().role() == ProjectMemberRole.DEVELOPER));
        assertTrue(members.stream().anyMatch(view ->
                view.member().userId().equals(MEMBER)
                        && view.member().role() == ProjectMemberRole.VIEWER));
    }

    @Test
    @DisplayName("项目标识在组织内唯一，不同组织可以使用同一个标识")
    void projectCodeIsUniqueInsideOrganization(@TempDir Path workspace) {
        Organization first = commandService.create(
                new CreateOrganizationCommand("project_org_a", "项目组织 A", null), OWNER);
        Organization second = commandService.create(
                new CreateOrganizationCommand("project_org_b", "项目组织 B", null), OWNER);
        RegisterProjectCommand command = new RegisterProjectCommand(
                "same_code", "同标识项目", workspace.toString(),
                null, null, DecisionLevel.L1);

        Project projectA = projectRegistrationService.register(command, OWNER, first.getId());
        Project projectB = projectRegistrationService.register(command, OWNER, second.getId());

        assertEquals(first.getId(), projectA.getOrganizationId());
        assertEquals(second.getId(), projectB.getOrganizationId());
        assertThrows(DomainException.class,
                () -> projectRegistrationService.register(
                        new RegisterProjectCommand(
                                "same_code", "重复项目", workspace.toString(),
                                null, null, DecisionLevel.L1),
                        OWNER, first.getId()));
    }

    @Test
    @DisplayName("管理员手动建号后必须修改初始密码才能继续使用")
    void manualAccountRequiresPasswordChange() {
        Organization organization = commandService.create(
                new CreateOrganizationCommand("manual_account_org", "手动账号组织", null), OWNER);

        var created = commandService.createAccount(
                organization.getId(),
                new CreateOrganizationAccountCommand(
                        "manual-user@example.com", "手动账号", OrganizationRole.ORG_MEMBER),
                OWNER);

        assertTrue(created.user().mustChangePassword());
        assertEquals(FixedTemporaryPasswordGenerator.DEFAULT_PASSWORD, created.initialPassword());
        assertThrows(DomainException.class, () -> commandService.createAccount(
                organization.getId(),
                new CreateOrganizationAccountCommand(
                        "manual-user@example.com", "重复账号", OrganizationRole.ORG_MEMBER),
                OWNER));

        var session = authService.login(new LoginCommand(
                "manual-user@example.com", created.initialPassword(), false));
        assertTrue(session.user().mustChangePassword());

        var changed = authService.changePassword(
                created.user().getId(), created.initialPassword(), "ChangedPass123");
        assertFalse(changed.mustChangePassword());
        authService.login(new LoginCommand(
                "manual-user@example.com", "ChangedPass123", false));
    }

    @Test
    @DisplayName("Excel 导入复用已有账号并创建带一次性密码的新账号")
    void importsExistingAndNewAccounts() {
        Organization organization = commandService.create(
                new CreateOrganizationCommand("excel_import_org", "Excel 导入组织", null), OWNER);

        var existing = commandService.importMember(
                organization.getId(),
                "org-member@example.com",
                "不会覆盖已有姓名",
                OrganizationRole.PROJECT_MANAGER,
                OWNER);
        assertFalse(existing.accountCreated());
        assertTrue(existing.roleAdded());
        assertNull(existing.initialPassword());
        assertEquals("组织成员", existing.user().getDisplayName().value());
        assertTrue(accessService.roles(organization.getId(), MEMBER)
                .contains(OrganizationRole.PROJECT_MANAGER));

        var created = commandService.importMember(
                organization.getId(),
                "excel-new@example.com",
                "Excel 新成员",
                OrganizationRole.ORG_MEMBER,
                OWNER);
        assertTrue(created.accountCreated());
        assertNotNull(created.initialPassword());
        assertEquals(FixedTemporaryPasswordGenerator.DEFAULT_PASSWORD, created.initialPassword());
        assertTrue(created.user().mustChangePassword());
        assertTrue(userRepository.findByEmail(
                com.pydlove.regent.identity.domain.model.Email.of("excel-new@example.com"))
                .isPresent());

        var duplicate = commandService.importMember(
                organization.getId(),
                "excel-new@example.com",
                "Excel 新成员",
                OrganizationRole.ORG_MEMBER,
                OWNER);
        assertFalse(duplicate.accountCreated());
        assertFalse(duplicate.roleAdded());
        assertThrows(DomainException.class, () -> commandService.importMember(
                organization.getId(),
                "excel-owner@example.com",
                "非法所有者",
                OrganizationRole.ORG_OWNER,
                OWNER));
    }

    @Test
    @DisplayName("组织管理员可以把成员密码重置为统一初始密码")
    void adminResetsMemberPasswordToDefault() {
        Organization organization = commandService.create(
                new CreateOrganizationCommand("password_reset_org", "密码重置组织", null), OWNER);
        var target = commandService.createAccount(
                organization.getId(),
                new CreateOrganizationAccountCommand(
                        "reset-target@example.com", "待重置成员", OrganizationRole.ORG_MEMBER),
                OWNER);
        var admin = commandService.createAccount(
                organization.getId(),
                new CreateOrganizationAccountCommand(
                        "reset-admin@example.com", "组织管理员", OrganizationRole.ORG_ADMIN),
                OWNER);

        String reset = commandService.resetMemberPassword(
                organization.getId(), target.user().getId(), admin.user().getId());

        assertEquals(FixedTemporaryPasswordGenerator.DEFAULT_PASSWORD, reset);
        assertTrue(userRepository.findById(target.user().getId()).orElseThrow()
                .mustChangePassword());
        assertThrows(DomainException.class, () -> commandService.resetMemberPassword(
                organization.getId(), OWNER, admin.user().getId()));
        assertThrows(DomainException.class, () -> commandService.resetMemberPassword(
                organization.getId(), admin.user().getId(), admin.user().getId()));
    }
}
