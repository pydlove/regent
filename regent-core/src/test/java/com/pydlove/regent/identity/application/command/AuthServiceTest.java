package com.pydlove.regent.identity.application.command;

import com.pydlove.regent.identity.application.query.AuthSessionGrant;
import com.pydlove.regent.identity.domain.model.AuthPrincipal;
import com.pydlove.regent.identity.domain.model.AuthSession;
import com.pydlove.regent.identity.domain.model.AuthSessionId;
import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.EmailVerificationCode;
import com.pydlove.regent.identity.domain.model.EmailVerificationCodeHash;
import com.pydlove.regent.identity.domain.model.PasswordHash;
import com.pydlove.regent.identity.domain.model.SessionTokenHash;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.port.EmailVerificationCodec;
import com.pydlove.regent.identity.domain.port.PasswordHasher;
import com.pydlove.regent.identity.domain.port.SessionTokenCodec;
import com.pydlove.regent.identity.domain.port.VerificationEmailSender;
import com.pydlove.regent.identity.domain.repository.AuthSessionRepository;
import com.pydlove.regent.identity.domain.repository.EmailVerificationCodeRepository;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.shared.domain.AuthenticationException;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");
    private static final String HASHED_TOKEN = "a".repeat(64);
    private static final String EMAIL_CODE = "123456";

    private InMemoryUserRepository users;
    private InMemorySessionRepository sessions;
    private InMemoryEmailCodeRepository codes;
    private MutableClock clock;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        sessions = new InMemorySessionRepository(users);
        codes = new InMemoryEmailCodeRepository();
        clock = new MutableClock(NOW);
        EmailVerificationService verification = new EmailVerificationService(
                users,
                codes,
                new FixedEmailCodec(),
                new NoopEmailSender(),
                clock,
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                10);
        service = new AuthService(
                users,
                sessions,
                new PlainTestHasher(),
                new FixedTokenCodec(),
                verification,
                clock,
                Duration.ofDays(7));
    }

    @Test
    @DisplayName("邮箱注册会规范化邮箱、派生唯一账号并消费验证码")
    void registersEmailAndConsumesCode() {
        var user = register("  Alice_01@Example.com  ");

        assertEquals("alice_01@example.com", user.email());
        assertEquals("alice_01", user.username());
        assertTrue(codes.findActive(Email.of("alice_01@example.com"), NOW).isEmpty());
        assertEquals("encoded:safe-pass-123",
                users.findByEmail(Email.of("alice_01@example.com")).orElseThrow().getPasswordHash().value());
    }

    @Test
    @DisplayName("邮箱重复、密码过短、确认密码不一致与验证码错误都明确拒绝")
    void rejectsInvalidRegistrationInputs() {
        register("alice@example.com");

        assertThrows(DomainException.class, () -> register("ALICE@example.com"));

        service.sendRegistrationCode("bob@example.com");
        DomainException weak = assertThrows(DomainException.class,
                () -> service.register(new RegisterEmailCommand(
                        "bob@example.com", EMAIL_CODE, "short", "short")));
        assertTrue(weak.getMessage().contains("至少"));

        DomainException mismatch = assertThrows(DomainException.class,
                () -> service.register(new RegisterEmailCommand(
                        "bob@example.com", EMAIL_CODE, "safe-pass-123", "safe-pass-456")));
        assertEquals("两次输入的密码不一致", mismatch.getMessage());

        DomainException wrongCode = assertThrows(DomainException.class,
                () -> service.register(new RegisterEmailCommand(
                        "bob@example.com", "000000", "safe-pass-123", "safe-pass-123")));
        assertEquals("邮箱验证码错误", wrongCode.getMessage());
    }

    @Test
    @DisplayName("验证码连续输错达到上限后立即失效")
    void invalidatesCodeAfterRepeatedFailures() {
        service.sendRegistrationCode("alice@example.com");
        for (int i = 0; i < 4; i++) {
            assertThrows(DomainException.class,
                    () -> service.register(new RegisterEmailCommand(
                            "alice@example.com", "000000", "safe-pass-123", "safe-pass-123")));
        }
        DomainException fifth = assertThrows(DomainException.class,
                () -> service.register(new RegisterEmailCommand(
                        "alice@example.com", "000000", "safe-pass-123", "safe-pass-123")));
        assertEquals("验证码错误次数过多，请重新获取", fifth.getMessage());
        assertTrue(codes.findActive(Email.of("alice@example.com"), NOW).isEmpty());
    }

    @Test
    @DisplayName("验证码发送有 60 秒冷却和每日上限")
    void limitsCodeSendingFrequency() {
        service.sendRegistrationCode("alice@example.com");
        DomainException tooFrequent = assertThrows(DomainException.class,
                () -> service.sendRegistrationCode("alice@example.com"));
        assertTrue(tooFrequent.getMessage().contains("频繁"));

        for (int i = 0; i < 9; i++) {
            clock.instant = clock.instant.plus(Duration.ofSeconds(61));
            service.sendRegistrationCode("alice@example.com");
        }
        clock.instant = clock.instant.plus(Duration.ofSeconds(61));
        DomainException dailyLimit = assertThrows(DomainException.class,
                () -> service.sendRegistrationCode("alice@example.com"));
        assertTrue(dailyLimit.getMessage().contains("上限"));
    }

    @Test
    @DisplayName("验证码过期后不能注册")
    void rejectsExpiredEmailCode() {
        service.sendRegistrationCode("alice@example.com");
        clock.instant = NOW.plus(Duration.ofMinutes(6));

        DomainException expired = assertThrows(DomainException.class,
                () -> service.register(new RegisterEmailCommand(
                        "alice@example.com", EMAIL_CODE, "safe-pass-123", "safe-pass-123")));
        assertTrue(expired.getMessage().contains("失效"));
    }

    @Test
    @DisplayName("邮箱和派生用户名都能登录，会话返回公开邮箱")
    void logsInByEmailOrUsernameAndAuthenticatesSession() {
        register("alice@example.com");

        AuthSessionGrant byEmail = service.login(new LoginCommand("ALICE@example.com", "safe-pass-123"));
        assertEquals("alice", byEmail.user().username());
        assertEquals("alice@example.com", byEmail.user().email());
        assertEquals(NOW.plus(Duration.ofDays(7)), byEmail.expiresAt());
        assertEquals(HASHED_TOKEN, sessions.saved.getTokenHash().value());
        assertFalse(sessions.saved.getTokenHash().value().contains("raw-token"));
        assertEquals("alice@example.com", service.currentUser("raw-token").email());

        AuthSessionGrant byUsername = service.login(new LoginCommand("ALICE", "safe-pass-123"));
        assertEquals("alice@example.com", byUsername.user().email());
    }

    @Test
    @DisplayName("历史用户名账号没有邮箱时仍可正常登录")
    void keepsLegacyUsernameLoginWorking() {
        users.seedLegacy(Username.of("legacy-user"), new PasswordHash("encoded:safe-pass-123"));

        AuthSessionGrant grant = service.login(new LoginCommand("legacy-user", "safe-pass-123"));

        assertEquals("legacy-user", grant.user().username());
        assertEquals(null, grant.user().email());
    }

    @Test
    @DisplayName("账号不存在和密码错误使用同一条认证失败消息")
    void doesNotRevealWhichCredentialWasWrong() {
        register("alice@example.com");

        AuthenticationException unknown = assertThrows(AuthenticationException.class,
                () -> service.login(new LoginCommand("nobody@example.com", "safe-pass-123")));
        AuthenticationException wrong = assertThrows(AuthenticationException.class,
                () -> service.login(new LoginCommand("alice@example.com", "wrong-pass")));

        assertEquals("账号或密码错误", unknown.getMessage());
        assertEquals(unknown.getMessage(), wrong.getMessage());
    }

    @Test
    @DisplayName("会话到期后认证失败，登出会让令牌立即失效")
    void expiresAndDeletesSessions() {
        register("alice@example.com");
        service.login(new LoginCommand("alice@example.com", "safe-pass-123"));

        clock.instant = NOW.plus(Duration.ofDays(8));
        assertTrue(service.authenticate("raw-token").isEmpty());

        clock.instant = NOW.plus(Duration.ofDays(1));
        service.login(new LoginCommand("alice@example.com", "safe-pass-123"));
        service.logout("raw-token");
        assertTrue(service.authenticate("raw-token").isEmpty());
    }

    @Test
    @DisplayName("异常超长 Cookie 不会进入仓储查询")
    void rejectsOversizedSessionTokenBeforeRepositoryLookup() {
        assertTrue(service.authenticate("x".repeat(129)).isEmpty());
    }

    private com.pydlove.regent.identity.application.query.AuthenticatedUser register(String email) {
        service.sendRegistrationCode(email);
        return service.register(new RegisterEmailCommand(
                email, EMAIL_CODE, "safe-pass-123", "safe-pass-123"));
    }

    private static final class PlainTestHasher implements PasswordHasher {

        @Override
        public PasswordHash hash(String rawPassword) {
            return new PasswordHash("encoded:" + rawPassword);
        }

        @Override
        public boolean matches(String rawPassword, PasswordHash encodedPassword) {
            return encodedPassword.value().equals(hash(rawPassword).value());
        }
    }

    private static final class FixedTokenCodec implements SessionTokenCodec {

        @Override
        public String generateRawToken() {
            return "raw-token";
        }

        @Override
        public SessionTokenHash hash(String rawToken) {
            return new SessionTokenHash(HASHED_TOKEN);
        }
    }

    private static final class FixedEmailCodec implements EmailVerificationCodec {

        @Override
        public String generateCode() {
            return EMAIL_CODE;
        }

        @Override
        public EmailVerificationCodeHash hash(String rawCode) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(rawCode.getBytes(StandardCharsets.UTF_8));
                return new EmailVerificationCodeHash(HexFormat.of().formatHex(digest));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static final class NoopEmailSender implements VerificationEmailSender {

        @Override
        public void sendRegistrationCode(Email email, String code, Duration ttl) {
        }
    }

    private static final class InMemoryUserRepository implements UserRepository {

        private final Map<String, User> byUsername = new HashMap<>();
        private final Map<String, User> byEmail = new HashMap<>();
        private final Map<Long, User> byId = new HashMap<>();
        private long nextId = 1;

        @Override
        public boolean existsByUsername(Username username) {
            return byUsername.containsKey(username.value());
        }

        @Override
        public boolean existsByEmail(Email email) {
            return byEmail.containsKey(email.value());
        }

        @Override
        public Optional<User> findByUsername(Username username) {
            return Optional.ofNullable(byUsername.get(username.value()));
        }

        @Override
        public Optional<User> findByEmail(Email email) {
            return Optional.ofNullable(byEmail.get(email.value()));
        }

        @Override
        public void save(User user) {
            user.assignIdentity(UserId.of(nextId++));
            byUsername.put(user.getUsername().value(), user);
            byEmail.put(user.getEmail().value(), user);
            byId.put(user.getId().value(), user);
        }

        private void seedLegacy(Username username, PasswordHash passwordHash) {
            long id = nextId++;
            User user = User.restore(UserId.of(id), null, username, passwordHash, NOW);
            byUsername.put(username.value(), user);
            byId.put(id, user);
        }

        private Optional<User> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }
    }

    private static final class InMemoryEmailCodeRepository implements EmailVerificationCodeRepository {

        private final Map<Long, EmailVerificationCode> codes = new HashMap<>();
        private long nextId = 1;

        @Override
        public Optional<EmailVerificationCode> findActive(Email email, Instant now) {
            return codes.values().stream()
                    .filter(code -> code.getEmail().equals(email) && code.isActive(now))
                    .max(Comparator.comparing(EmailVerificationCode::getId));
        }

        @Override
        public Optional<Instant> findLatestCreatedAt(Email email) {
            return codes.values().stream()
                    .filter(code -> code.getEmail().equals(email))
                    .map(EmailVerificationCode::getCreatedAt)
                    .max(Comparator.naturalOrder());
        }

        @Override
        public long countCreatedSince(Email email, Instant since) {
            return codes.values().stream()
                    .filter(code -> code.getEmail().equals(email))
                    .filter(code -> !code.getCreatedAt().isBefore(since))
                    .count();
        }

        @Override
        public void save(EmailVerificationCode code) {
            if (code.getId() == null) {
                code.assignIdentity(nextId++);
            }
            codes.put(code.getId(), code);
        }
    }

    private static final class InMemorySessionRepository implements AuthSessionRepository {

        private final InMemoryUserRepository users;
        private AuthSession saved;

        private InMemorySessionRepository(InMemoryUserRepository users) {
            this.users = users;
        }

        @Override
        public void save(AuthSession session) {
            session.assignIdentity(AuthSessionId.of(1));
            saved = session;
        }

        @Override
        public Optional<AuthPrincipal> findActivePrincipal(SessionTokenHash tokenHash, Instant now) {
            if (saved == null
                    || !saved.getTokenHash().equals(tokenHash)
                    || !saved.getExpiresAt().isAfter(now)) {
                return Optional.empty();
            }
            return users.findById(saved.getUserId().value())
                    .map(user -> new AuthPrincipal(
                            user.getId(), user.getUsername(), user.getEmail()));
        }

        @Override
        public void deleteByTokenHash(SessionTokenHash tokenHash) {
            if (saved != null && saved.getTokenHash().equals(tokenHash)) {
                saved = null;
            }
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
