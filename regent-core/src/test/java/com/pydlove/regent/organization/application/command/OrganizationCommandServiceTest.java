package com.pydlove.regent.organization.application.command;

import com.pydlove.regent.identity.application.command.UserProvisioningService;
import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.PasswordHash;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.port.PasswordHasher;
import com.pydlove.regent.identity.domain.port.TemporaryPasswordGenerator;
import com.pydlove.regent.identity.domain.repository.UserRepository;
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
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizationCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T08:00:00Z");
    private static final UserId OWNER = UserId.of(10L);
    private static final String RAW_TOKEN = "invite-token-123";

    @Test
    @DisplayName("邀请创建后发送邮件，并使用公开域名生成邀请链接")
    void sendsInvitationEmailWithPublicUrl() {
        Fixture fixture = new Fixture("https://ichuang.top/regent/", false);

        CreatedOrganizationInvitation created = fixture.invite();

        assertTrue(created.emailSent());
        assertEquals("https://ichuang.top/regent/invite/" + RAW_TOKEN,
                created.invitationUrl());
        assertEquals("Regent 研发中心", fixture.mail.organizationName);
        assertEquals("https://ichuang.top/regent/invite/" + RAW_TOKEN, fixture.mail.url);
        assertEquals(Email.of("new-member@example.com"), fixture.mail.recipient);
        assertEquals(Duration.ofDays(7), fixture.mail.ttl);
        assertEquals(1, fixture.invitations.saved.size());
    }

    @Test
    @DisplayName("邮件发送失败时邀请仍保留，并返回可复制的链接")
    void keepsInvitationWhenEmailFails() {
        Fixture fixture = new Fixture("https://ichuang.top/regent", true);

        CreatedOrganizationInvitation created = fixture.invite();

        assertFalse(created.emailSent());
        assertTrue(created.invitationUrl().endsWith("/invite/" + RAW_TOKEN));
        assertEquals(1, fixture.invitations.saved.size());
        assertTrue(fixture.mail.attempted);
    }

    private static final class Fixture {

        private final Organizations organizations = new Organizations();
        private final Members members = new Members();
        private final Invitations invitations = new Invitations();
        private final MailSender mail = new MailSender();
        private final OrganizationCommandService service;

        private Fixture(String publicUrl, boolean failMail) {
            mail.fail = failMail;
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            UserRepository users = new Users();
            UserProvisioningService provisioning = new UserProvisioningService(
                    users,
                    new PasswordHasher() {
                        @Override
                        public PasswordHash hash(String rawPassword) {
                            return new PasswordHash("hashed");
                        }

                        @Override
                        public boolean matches(String rawPassword, PasswordHash encodedPassword) {
                            return false;
                        }
                    },
                    clock);
            service = new OrganizationCommandService(
                    organizations,
                    members,
                    invitations,
                    new Audits(),
                    new TokenCodec(),
                    mail,
                    new OrganizationAccessService(members),
                    users,
                    provisioning,
                    () -> "temporary-password",
                    new DirectTransactions(),
                    clock,
                    Duration.ofDays(7),
                    publicUrl);
        }

        private CreatedOrganizationInvitation invite() {
            Organization organization = organizations.save(Organization.create(
                    "regent_lab", "Regent 研发中心", null, OWNER));
            return service.invite(
                    organization.getId(),
                    new InviteOrganizationMemberCommand(
                            "new-member@example.com", OrganizationRole.ORG_MEMBER),
                    OWNER);
        }
    }

    private static final class Organizations implements OrganizationRepository {

        private Organization saved;

        @Override
        public Organization save(Organization organization) {
            organization.assignIdentity(OrganizationId.of(7L));
            saved = organization;
            return organization;
        }

        @Override
        public void update(Organization organization) {
            saved = organization;
        }

        @Override
        public Optional<Organization> findById(OrganizationId id) {
            return saved != null && saved.getId().equals(id)
                    ? Optional.of(saved)
                    : Optional.empty();
        }

        @Override
        public Optional<Organization> findByCode(String code) {
            return Optional.empty();
        }

        @Override
        public List<Organization> findByIds(List<OrganizationId> ids) {
            return List.of();
        }
    }

    private static final class Members implements OrganizationMemberRepository {

        @Override
        public void save(OrganizationMember member) {
        }

        @Override
        public List<OrganizationMember> findByOrganization(OrganizationId organizationId) {
            return List.of();
        }

        @Override
        public List<OrganizationMember> findByUser(UserId userId) {
            return List.of();
        }

        @Override
        public List<OrganizationRole> findRoles(OrganizationId organizationId, UserId userId) {
            return List.of(OrganizationRole.ORG_ADMIN);
        }

        @Override
        public boolean exists(OrganizationId organizationId,
                              UserId userId,
                              OrganizationRole role) {
            return false;
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
            return 0;
        }

        @Override
        public int countByRole(OrganizationId organizationId, OrganizationRole role) {
            return 0;
        }
    }

    private static final class Invitations implements OrganizationInvitationRepository {

        private final List<OrganizationInvitation> saved = new ArrayList<>();

        @Override
        public OrganizationInvitation save(OrganizationInvitation invitation) {
            invitation.assignIdentity(saved.size() + 1L);
            saved.add(invitation);
            return invitation;
        }

        @Override
        public void update(OrganizationInvitation invitation) {
        }

        @Override
        public Optional<OrganizationInvitation> findByTokenHash(String tokenHash) {
            return Optional.empty();
        }

        @Override
        public Optional<OrganizationInvitation> findPending(OrganizationId organizationId,
                                                            Email email) {
            return Optional.empty();
        }

        @Override
        public List<OrganizationInvitation> findByOrganization(OrganizationId organizationId) {
            return List.copyOf(saved);
        }
    }

    private static final class MailSender implements OrganizationInvitationEmailSender {

        private boolean fail;
        private boolean attempted;
        private Email recipient;
        private String organizationName;
        private String url;
        private Duration ttl;

        @Override
        public void sendInvitation(Email recipient,
                                   String organizationName,
                                   String invitationUrl,
                                   Duration ttl) {
            attempted = true;
            this.recipient = recipient;
            this.organizationName = organizationName;
            url = invitationUrl;
            this.ttl = ttl;
            if (fail) {
                throw new IllegalStateException("SMTP unavailable");
            }
        }
    }

    private static final class Users implements UserRepository {

        @Override
        public boolean existsByUsername(Username username) {
            return false;
        }

        @Override
        public boolean existsByEmail(Email email) {
            return false;
        }

        @Override
        public Optional<User> findByUsername(Username username) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByEmail(Email email) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.empty();
        }

        @Override
        public void save(User user) {
        }

        @Override
        public void update(User user) {
        }
    }

    private static final class TokenCodec implements OrganizationInvitationTokenCodec {

        @Override
        public String generateRawToken() {
            return RAW_TOKEN;
        }

        @Override
        public String hash(String rawToken) {
            return "a".repeat(64);
        }
    }

    private static final class Audits implements OrganizationAuditRepository {

        @Override
        public void save(OrganizationAuditEntry entry) {
        }

        @Override
        public List<OrganizationAuditEntry> findRecent(OrganizationId organizationId, int limit) {
            return List.of();
        }
    }

    private static final class DirectTransactions implements TransactionRunner {

        @Override
        public <T> T inTransaction(Supplier<T> action) {
            return action.get();
        }
    }
}
