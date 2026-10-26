package com.pydlove.regent.identity.application.command;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.application.query.MobileAuthGrant;
import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.InstallationId;
import com.pydlove.regent.identity.domain.model.MobileAuthSession;
import com.pydlove.regent.identity.domain.model.MobileAuthSessionId;
import com.pydlove.regent.identity.domain.model.PasswordHash;
import com.pydlove.regent.identity.domain.model.SessionTokenHash;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.port.SessionTokenCodec;
import com.pydlove.regent.identity.domain.repository.MobileAuthSessionRepository;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.shared.domain.AuthenticationException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
class MobileAuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T01:00:00Z");

    private InMemoryUserRepository users;
    private InMemoryMobileSessionRepository sessions;
    private MutableClock clock;
    private MobileAuthService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        sessions = new InMemoryMobileSessionRepository();
        clock = new MutableClock(NOW);
        service = new MobileAuthService(
                command -> new AuthenticatedUser(
                        7, "alice", "Alice", "alice@example.com", null),
                users,
                sessions,
                new SequenceTokenCodec(),
                directTransactions(),
                clock,
                Duration.ofMinutes(30),
                Duration.ofDays(30));
    }

    @Test
    @DisplayName("登录签发独立访问令牌和刷新令牌，访问令牌可直接认证")
    void loginIssuesIndependentTokenPair() {
        users.create(7, "alice");

        MobileAuthGrant grant = service.login(
                new LoginCommand("alice", "secret", false),
                "install-12345678");

        assertTrue(grant.accessToken().startsWith("mat_"));
        assertTrue(grant.refreshToken().startsWith("mrt_"));
        assertNotEquals(grant.accessToken(), grant.refreshToken());
        assertEquals(NOW.plus(Duration.ofMinutes(30)), grant.accessExpiresAt());
        assertEquals(NOW.plus(Duration.ofDays(30)), grant.refreshExpiresAt());
        assertEquals("alice", service.authenticate(grant.accessToken()).orElseThrow().username());
        assertEquals(1, sessions.store.size());
        assertFalse(sessions.store.values().iterator().next().getAccessTokenHash().value()
                .contains(grant.accessToken()));
    }

    @Test
    @DisplayName("任意历史刷新令牌重放都会撤销整个安装会话")
    void refreshRotatesAndDetectsReplay() {
        users.create(7, "alice");
        MobileAuthGrant first = service.login(
                new LoginCommand("alice", "secret", false),
                "install-12345678");
        clock.instant = NOW.plus(Duration.ofMinutes(10));

        MobileAuthGrant second = service.refresh(first.refreshToken());
        clock.instant = NOW.plus(Duration.ofMinutes(20));
        MobileAuthGrant third = service.refresh(second.refreshToken());

        assertTrue(service.authenticate(first.accessToken()).isEmpty());
        assertTrue(service.authenticate(third.accessToken()).isPresent());
        clock.instant = NOW.plus(Duration.ofMinutes(21));
        AuthenticationException replay = assertThrows(
                AuthenticationException.class,
                () -> service.refresh(first.refreshToken()));
        assertEquals("登录已失效，请重新登录", replay.getMessage());
        assertTrue(service.authenticate(third.accessToken()).isEmpty());
    }

    @Test
    @DisplayName("同一安装重新登录会撤销此前令牌，同一账号其他安装不受影响")
    void replacesOnlySameInstallation() {
        users.create(7, "alice");
        MobileAuthGrant phoneA = service.login(
                new LoginCommand("alice", "secret", false), "install-aaaaaaaa");
        MobileAuthGrant tablet = service.login(
                new LoginCommand("alice", "secret", false), "install-bbbbbbbb");
        MobileAuthGrant phoneARelogin = service.login(
                new LoginCommand("alice", "secret", false), "install-aaaaaaaa");

        assertTrue(service.authenticate(phoneA.accessToken()).isEmpty());
        assertTrue(service.authenticate(tablet.accessToken()).isPresent());
        assertTrue(service.authenticate(phoneARelogin.accessToken()).isPresent());
    }

    @Test
    @DisplayName("登出后访问令牌立即失效")
    void logoutRevokesAccessToken() {
        users.create(7, "alice");
        MobileAuthGrant grant = service.login(
                new LoginCommand("alice", "secret", false), "install-12345678");

        service.logout(grant.accessToken());

        assertTrue(service.authenticate(grant.accessToken()).isEmpty());
        assertThrows(AuthenticationException.class, () -> service.refresh(grant.refreshToken()));
    }

    private static TransactionRunner directTransactions() {
        return new TransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> action) {
                return action.get();
            }
        };
    }

    private static final class SequenceTokenCodec implements SessionTokenCodec {

        private int sequence;

        @Override
        public String generateRawToken() {
            return "raw-" + (++sequence);
        }

        @Override
        public SessionTokenHash hash(String rawToken) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(rawToken.getBytes(StandardCharsets.UTF_8));
                return new SessionTokenHash(HexFormat.of().formatHex(digest));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
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

    private static final class InMemoryUserRepository implements UserRepository {

        private final Map<Long, User> store = new LinkedHashMap<>();

        private void create(long id, String username) {
            User user = User.register(
                    Email.of(username + "@example.com"),
                    Username.of(username),
                    new PasswordHash("encoded"),
                    NOW);
            user.assignIdentity(UserId.of(id));
            store.put(id, user);
        }

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
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public void save(User user) {
            store.put(user.getId().value(), user);
        }

        @Override
        public void update(User user) {
            store.put(user.getId().value(), user);
        }
    }

    private static final class InMemoryMobileSessionRepository
            implements MobileAuthSessionRepository {

        private final Map<Long, MobileAuthSession> store = new LinkedHashMap<>();
        private final Map<String, MobileAuthSessionId> refreshHistory = new LinkedHashMap<>();
        private long sequence;

        @Override
        public void save(MobileAuthSession session) {
            session.assignIdentity(
                    com.pydlove.regent.identity.domain.model.MobileAuthSessionId.of(++sequence));
            store.put(session.getId().value(), session);
        }

        @Override
        public void update(MobileAuthSession session) {
            store.put(session.getId().value(), session);
        }

        @Override
        public Optional<MobileAuthSession> findActiveByAccessToken(SessionTokenHash tokenHash,
                                                                   Instant now) {
            return store.values().stream()
                    .filter(session -> session.accessActiveAt(now))
                    .filter(session -> session.getAccessTokenHash().equals(tokenHash))
                    .findFirst();
        }

        @Override
        public Optional<MobileAuthSession> findByRefreshToken(SessionTokenHash tokenHash) {
            return store.values().stream()
                    .filter(session -> session.getRefreshTokenHash().equals(tokenHash)
                            || session.isPreviousRefreshToken(tokenHash))
                    .max(Comparator.comparing(session -> session.getId().value()));
        }

        @Override
        public Optional<MobileAuthSession> findByIdForUpdate(MobileAuthSessionId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public Optional<MobileAuthSessionId> findSessionIdByUsedRefreshToken(
                SessionTokenHash tokenHash) {
            return Optional.ofNullable(refreshHistory.get(tokenHash.value()));
        }

        @Override
        public void recordRefreshTokenUse(MobileAuthSessionId sessionId,
                                          SessionTokenHash tokenHash,
                                          Instant usedAt) {
            refreshHistory.put(tokenHash.value(), sessionId);
        }

        @Override
        public void revokeActiveByInstallation(UserId userId,
                                               InstallationId installationId,
                                               Instant now) {
            store.values().stream()
                    .filter(session -> session.getUserId().equals(userId))
                    .filter(session -> session.getInstallationId().equals(installationId))
                    .forEach(session -> session.revoke(now));
        }
    }
}
