package com.pydlove.regent.organization.application.command;

import com.pydlove.regent.identity.application.command.UserProvisioningService;
import com.pydlove.regent.identity.domain.model.DisplayName;
import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.identity.domain.port.TemporaryPasswordGenerator;
import com.pydlove.regent.organization.application.query.AcceptedOrganizationInvitation;
import com.pydlove.regent.organization.application.query.CreatedOrganizationAccount;
import com.pydlove.regent.organization.application.query.CreatedOrganizationInvitation;
import com.pydlove.regent.organization.application.query.OrganizationAccessService;
import com.pydlove.regent.organization.domain.model.Organization;
import com.pydlove.regent.organization.domain.model.OrganizationAuditEntry;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.model.OrganizationInvitation;
import com.pydlove.regent.organization.domain.model.OrganizationMember;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.organization.domain.port.OrganizationInvitationEmailSender;
import com.pydlove.regent.organization.domain.port.OrganizationInvitationTokenCodec;
import com.pydlove.regent.organization.domain.repository.OrganizationAuditRepository;
import com.pydlove.regent.organization.domain.repository.OrganizationInvitationRepository;
import com.pydlove.regent.organization.domain.repository.OrganizationMemberRepository;
import com.pydlove.regent.organization.domain.repository.OrganizationRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.ForbiddenException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 组织创建、成员角色与邀请账号的写侧用例。 */
public class OrganizationCommandService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationInvitationRepository invitationRepository;
    private final OrganizationAuditRepository auditRepository;
    private final OrganizationInvitationTokenCodec tokenCodec;
    private final OrganizationInvitationEmailSender invitationEmailSender;
    private final OrganizationAccessService accessService;
    private final UserRepository userRepository;
    private final UserProvisioningService userProvisioningService;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final TransactionRunner transactionRunner;
    private final Clock clock;
    private final Duration invitationTtl;
    private final String invitationBaseUrl;

    public OrganizationCommandService(OrganizationRepository organizationRepository,
                                      OrganizationMemberRepository memberRepository,
                                      OrganizationInvitationRepository invitationRepository,
                                      OrganizationAuditRepository auditRepository,
                                      OrganizationInvitationTokenCodec tokenCodec,
                                      OrganizationInvitationEmailSender invitationEmailSender,
                                      OrganizationAccessService accessService,
                                      UserRepository userRepository,
                                      UserProvisioningService userProvisioningService,
                                      TemporaryPasswordGenerator temporaryPasswordGenerator,
                                      TransactionRunner transactionRunner,
                                      Clock clock,
                                      Duration invitationTtl,
                                      String invitationBaseUrl) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.invitationRepository = invitationRepository;
        this.auditRepository = auditRepository;
        this.tokenCodec = tokenCodec;
        this.invitationEmailSender = invitationEmailSender;
        this.accessService = accessService;
        this.userRepository = userRepository;
        this.userProvisioningService = userProvisioningService;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
        this.invitationTtl = invitationTtl;
        this.invitationBaseUrl = invitationBaseUrl;
    }

    public Organization create(CreateOrganizationCommand command, UserId actorId) {
        return transactionRunner.inTransaction(() -> {
            List<OrganizationMember> memberships = memberRepository.findByUser(actorId);
            boolean mayCreate = memberships.isEmpty()
                    || memberships.stream()
                    .anyMatch(member -> member.role().canManageOrganization());
            if (!mayCreate) {
                throw new ForbiddenException("只有组织所有者或组织管理员可以创建组织");
            }
            String code = command.code() == null ? null : command.code().trim();
            if (code != null && organizationRepository.findByCode(code).isPresent()) {
                throw new DomainException("组织标识已被占用：" + code);
            }
            Organization organization = Organization.create(
                    command.code(), command.name(), command.description(), actorId);
            Organization saved = organizationRepository.save(organization);
            memberRepository.save(OrganizationMember.assign(
                    saved.getId(), actorId, OrganizationRole.ORG_OWNER));
            audit(saved.getId(), actorId, "ORGANIZATION_CREATED", "ORGANIZATION",
                    saved.getId().value(), null);
            return saved;
        });
    }

    public Organization update(OrganizationId id,
                               UpdateOrganizationCommand command,
                               UserId actorId) {
        return transactionRunner.inTransaction(() -> {
            accessService.requireAdmin(id, actorId);
            Organization organization = requireOrganization(id);
            organization.updateDetails(command.name(), command.description());
            organizationRepository.update(organization);
            audit(id, actorId, "ORGANIZATION_UPDATED", "ORGANIZATION", id.value(), null);
            return organization;
        });
    }

    public CreatedOrganizationInvitation invite(OrganizationId id,
                                                InviteOrganizationMemberCommand command,
                                                UserId actorId) {
        CreatedOrganizationInvitation created = transactionRunner.inTransaction(() -> {
            accessService.requireAdmin(id, actorId);
            Organization organization = requireOrganization(id);
            Email email = Email.of(command.email());
            OrganizationRole role = command.role();
            if (role == null || role == OrganizationRole.ORG_OWNER) {
                throw new DomainException("邀请只能授予组织管理员、项目经理或普通成员");
            }
            UserId existingUserId = findUserId(email);
            List<OrganizationRole> existingRoles = existingUserId == null
                    ? List.of()
                    : memberRepository.findRoles(id, existingUserId);
            if (!existingRoles.isEmpty() && existingRoles.contains(role)) {
                throw new DomainException("该账号已经是组织成员并拥有此角色");
            }
            invitationRepository.findPending(id, email).ifPresent(existing -> {
                existing.revoke();
                invitationRepository.update(existing);
            });

            String rawToken = tokenCodec.generateRawToken();
            Instant expiresAt = clock.instant().plus(invitationTtl);
            OrganizationInvitation invitation = OrganizationInvitation.create(
                    id, email, role, tokenCodec.hash(rawToken), expiresAt, actorId);
            OrganizationInvitation saved = invitationRepository.save(invitation);
            audit(id, actorId, "INVITATION_CREATED", "INVITATION",
                    saved.getId(), "{\"email\":\"" + email.value() + "\",\"role\":\"" + role + "\"}");
            return new CreatedOrganizationInvitation(
                    saved,
                    rawToken,
                    invitationUrl(rawToken),
                    organization.getName(),
                    false);
        });
        try {
            invitationEmailSender.sendInvitation(
                    created.invitation().getEmail(),
                    created.organizationName(),
                    created.invitationUrl(),
                    invitationTtl);
            return created.withEmailSent(true);
        } catch (RuntimeException ignored) {
            // 邮件失败不能让已经创建的邀请消失；接口会把链接返回给管理员兜底。
            return created;
        }
    }

    public CreatedOrganizationAccount createAccount(OrganizationId id,
                                                    CreateOrganizationAccountCommand command,
                                                    UserId actorId) {
        return transactionRunner.inTransaction(() -> {
            accessService.requireAdmin(id, actorId);
            requireOrganization(id);
            OrganizationRole role = command.role();
            if (role == null || role == OrganizationRole.ORG_OWNER) {
                throw new DomainException("手动创建账号只能授予组织管理员、项目经理或普通成员");
            }
            Email email = Email.of(command.email());
            if (userRepository.existsByEmail(email)) {
                throw new DomainException("该邮箱已注册，请直接在成员目录中分配角色");
            }
            String initialPassword = temporaryPasswordGenerator.generate();
            User user = userProvisioningService.createTemporary(
                    email,
                    DisplayName.of(command.displayName()),
                    initialPassword);
            memberRepository.save(OrganizationMember.assign(id, user.getId(), role));
            audit(id, actorId, "ACCOUNT_CREATED", "USER", user.getId().value(),
                    "{\"email\":\"" + email.value() + "\",\"role\":\"" + role + "\"}");
            return new CreatedOrganizationAccount(user, initialPassword);
        });
    }

    /**
     * 导入一行组织成员。
     *
     * <p>按邮箱判断账号是否已存在：已有账号只补组织角色，不覆盖登录名和显示名称；
     * 新邮箱创建一次性账号。每一行单独事务，某一行失败不会回滚已经导入成功的行。
     */
    public OrganizationMemberImportResult importMember(OrganizationId id,
                                                       String emailValue,
                                                       String displayNameValue,
                                                       OrganizationRole role,
                                                       UserId actorId) {
        return transactionRunner.inTransaction(() -> {
            accessService.requireAdmin(id, actorId);
            requireOrganization(id);
            if (role == null || role == OrganizationRole.ORG_OWNER) {
                throw new DomainException("导入只能授予组织管理员、项目经理或普通成员");
            }
            Email email = Email.of(emailValue);
            User existing = userRepository.findByEmail(email).orElse(null);
            if (existing != null) {
                List<OrganizationRole> roles = memberRepository.findRoles(id, existing.getId());
                boolean roleAdded = !roles.contains(role);
                if (roleAdded) {
                    memberRepository.save(OrganizationMember.assign(id, existing.getId(), role));
                    audit(id, actorId, "MEMBER_ROLE_ADDED", "USER", existing.getId().value(),
                            "{\"role\":\"" + role + "\",\"source\":\"EXCEL_IMPORT\"}");
                }
                return new OrganizationMemberImportResult(
                        existing, role, false, roleAdded, null);
            }

            if (displayNameValue == null || displayNameValue.isBlank()) {
                throw new DomainException("新账号必须填写姓名");
            }
            String initialPassword = temporaryPasswordGenerator.generate();
            User user = userProvisioningService.createTemporary(
                    email,
                    DisplayName.of(displayNameValue),
                    initialPassword);
            memberRepository.save(OrganizationMember.assign(id, user.getId(), role));
            audit(id, actorId, "ACCOUNT_CREATED", "USER", user.getId().value(),
                    "{\"email\":\"" + email.value() + "\",\"role\":\"" + role
                            + "\",\"source\":\"EXCEL_IMPORT\"}");
            return new OrganizationMemberImportResult(
                    user, role, true, true, initialPassword);
        });
    }

    public String resetMemberPassword(OrganizationId id,
                                      UserId targetUserId,
                                      UserId actorId) {
        return transactionRunner.inTransaction(() -> {
            accessService.requireAdmin(id, actorId);
            if (targetUserId.equals(actorId)) {
                throw new DomainException("请在个人设置中修改自己的密码");
            }
            userRepository.findById(targetUserId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "账号不存在：" + targetUserId.value()));
            List<OrganizationRole> roles = memberRepository.findRoles(id, targetUserId);
            if (roles.isEmpty()) {
                throw new EntityNotFoundException("该账号不是当前组织成员");
            }
            if (roles.contains(OrganizationRole.ORG_OWNER)) {
                throw new DomainException("不能重置组织所有者的密码");
            }
            String initialPassword = temporaryPasswordGenerator.generate();
            userProvisioningService.resetTemporaryPassword(targetUserId, initialPassword);
            audit(id, actorId, "MEMBER_PASSWORD_RESET", "USER", targetUserId.value(), null);
            return initialPassword;
        });
    }

    public void revokeInvitation(OrganizationId id, long invitationId, UserId actorId) {
        transactionRunner.inTransaction(() -> {
            accessService.requireAdmin(id, actorId);
            OrganizationInvitation invitation = invitationRepository.findByOrganization(id).stream()
                    .filter(item -> item.getId() != null && item.getId() == invitationId)
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("邀请不存在：" + invitationId));
            invitation.revoke();
            invitationRepository.update(invitation);
            audit(id, actorId, "INVITATION_REVOKED", "INVITATION", invitationId, null);
            return null;
        });
    }

    public void addRole(OrganizationId id, UserId targetUserId,
                        OrganizationRole role, UserId actorId) {
        transactionRunner.inTransaction(() -> {
            accessService.requireAdmin(id, actorId);
            if (role == null || role == OrganizationRole.ORG_OWNER) {
                throw new DomainException("不能通过成员角色接口授予组织所有者角色");
            }
            userRepository.findById(targetUserId)
                    .orElseThrow(() -> new EntityNotFoundException("账号不存在：" + targetUserId.value()));
            if (!memberRepository.exists(id, targetUserId, role)) {
                memberRepository.save(OrganizationMember.assign(id, targetUserId, role));
            }
            audit(id, actorId, "MEMBER_ROLE_ADDED", "USER", targetUserId.value(),
                    "{\"role\":\"" + role + "\"}");
            return null;
        });
    }

    public void removeRole(OrganizationId id, UserId targetUserId,
                           OrganizationRole role, UserId actorId) {
        transactionRunner.inTransaction(() -> {
            accessService.requireAdmin(id, actorId);
            if (role == null || role == OrganizationRole.ORG_OWNER) {
                throw new DomainException("组织所有者角色不能移除");
            }
            memberRepository.deleteRole(id, targetUserId, role);
            audit(id, actorId, "MEMBER_ROLE_REMOVED", "USER", targetUserId.value(),
                    "{\"role\":\"" + role + "\"}");
            return null;
        });
    }

    public void removeMember(OrganizationId id, UserId targetUserId, UserId actorId) {
        transactionRunner.inTransaction(() -> {
            accessService.requireAdmin(id, actorId);
            List<OrganizationRole> roles = memberRepository.findRoles(id, targetUserId);
            if (roles.contains(OrganizationRole.ORG_OWNER)) {
                throw new DomainException("不能移除组织所有者");
            }
            memberRepository.deleteMember(id, targetUserId);
            audit(id, actorId, "MEMBER_REMOVED", "USER", targetUserId.value(), null);
            return null;
        });
    }

    public AcceptedOrganizationInvitation acceptInvitation(String rawToken,
                                                           AcceptOrganizationInvitationCommand command,
                                                           UserId authenticatedUserId) {
        return transactionRunner.inTransaction(() -> {
            if (rawToken == null || rawToken.isBlank()) {
                throw new DomainException("邀请令牌不能为空");
            }
            OrganizationInvitation invitation = invitationRepository
                    .findByTokenHash(tokenCodec.hash(rawToken))
                    .orElseThrow(() -> new EntityNotFoundException("邀请不存在或已失效"));
            Instant now = clock.instant();
            invitation.requireUsable(now);
            Organization organization = requireOrganization(invitation.getOrganizationId());

            User user = userRepository.findByEmail(invitation.getEmail()).orElse(null);
            if (user != null) {
                if (authenticatedUserId == null) {
                    throw new DomainException("该邮箱已有账号，请先登录后接受邀请");
                }
                if (!user.getId().equals(authenticatedUserId)) {
                    throw new DomainException("当前登录账号与邀请邮箱不一致");
                }
            } else {
                if (authenticatedUserId != null) {
                    User actor = userRepository.findById(authenticatedUserId)
                            .orElseThrow(() -> new EntityNotFoundException("当前账号不存在"));
                    if (!invitation.getEmail().equals(actor.getEmail())) {
                        throw new DomainException("当前登录账号与邀请邮箱不一致");
                    }
                } else {
                    if (command == null || command.password() == null
                            || command.displayName() == null) {
                        throw new DomainException("新账号需要填写显示名称和密码");
                    }
                    user = userProvisioningService.createInvited(
                            invitation.getEmail(),
                            DisplayName.of(command.displayName()),
                            command.password());
                }
            }

            if (!memberRepository.exists(
                    invitation.getOrganizationId(), user.getId(), invitation.getRole())) {
                memberRepository.save(OrganizationMember.assign(
                        invitation.getOrganizationId(), user.getId(), invitation.getRole()));
            }
            invitation.accept(user.getId(), now);
            invitationRepository.update(invitation);
            audit(invitation.getOrganizationId(), user.getId(), "INVITATION_ACCEPTED",
                    "INVITATION", invitation.getId(), null);
            return new AcceptedOrganizationInvitation(organization, user);
        });
    }

    private Organization requireOrganization(OrganizationId id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("组织不存在：" + id.value()));
    }

    private UserId findUserId(Email email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElse(null);
    }

    private String invitationUrl(String rawToken) {
        String base = invitationBaseUrl == null ? "" : invitationBaseUrl.trim();
        if (base.isEmpty()) {
            base = "https://ichuang.top/regent";
        }
        return base.replaceAll("/+$", "") + "/invite/" + rawToken;
    }

    private void audit(OrganizationId organizationId,
                       UserId actorId,
                       String action,
                       String targetType,
                       Long targetId,
                       String detailJson) {
        auditRepository.save(OrganizationAuditEntry.record(
                organizationId, actorId, action, targetType, targetId, detailJson));
    }
}
