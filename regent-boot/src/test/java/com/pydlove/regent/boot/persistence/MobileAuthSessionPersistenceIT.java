package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.identity.domain.model.InstallationId;
import com.pydlove.regent.identity.domain.model.MobileAuthSession;
import com.pydlove.regent.identity.domain.model.SessionTokenHash;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.repository.MobileAuthSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MobileAuthSessionPersistenceIT {

    private static final UserId USER = UserId.of(998_001L);
    private static final InstallationId INSTALLATION = InstallationId.of("install-persistence-001");
    private static final Instant NOW = Instant.parse("2026-10-21T04:00:00Z");
    private static final SessionTokenHash ACCESS = hash('1');
    private static final SessionTokenHash REFRESH = hash('2');
    private static final SessionTokenHash NEXT_ACCESS = hash('3');
    private static final SessionTokenHash NEXT_REFRESH = hash('4');

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MobileAuthSessionRepository repository;

    @BeforeEach
    void prepareUser() {
        jdbcTemplate.update("""
                INSERT INTO app_user (id, username, display_name, password_hash, email, created_at)
                VALUES (?, 'mobile_persistence', '手机持久化', 'x',
                        'mobile-persistence@example.com', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """, USER.value());
    }

    @Test
    @DisplayName("手机双令牌可落库、轮换并按安装实例撤销")
    void persistsRotatesAndRevokesMobileSession() {
        MobileAuthSession session = MobileAuthSession.open(
                USER,
                INSTALLATION,
                ACCESS,
                REFRESH,
                NOW.plusSeconds(1800),
                NOW.plusSeconds(2_592_000),
                NOW);
        repository.save(session);

        assertTrue(session.getId() != null);
        assertEquals(session.getId().value(), repository
                .findActiveByAccessToken(ACCESS, NOW)
                .orElseThrow()
                .getId()
                .value());

        MobileAuthSession loaded = repository.findActiveByAccessToken(
                        ACCESS, NOW.plusSeconds(1))
                .orElseThrow();
        loaded.rotate(
                REFRESH,
                NEXT_ACCESS,
                NEXT_REFRESH,
                NOW.plusSeconds(2),
                NOW.plusSeconds(1802),
                NOW.plusSeconds(2_592_002));
        repository.update(loaded);

        assertFalse(repository.findActiveByAccessToken(ACCESS, NOW.plusSeconds(3)).isPresent());
        assertTrue(repository.findActiveByAccessToken(NEXT_ACCESS, NOW.plusSeconds(3)).isPresent());
        assertTrue(repository.findByRefreshToken(REFRESH).isPresent());

        repository.revokeActiveByInstallation(USER, INSTALLATION, NOW.plusSeconds(4));
        assertFalse(repository.findActiveByAccessToken(NEXT_ACCESS, NOW.plusSeconds(5)).isPresent());
    }

    private static SessionTokenHash hash(char value) {
        return new SessionTokenHash(String.valueOf(value).repeat(64));
    }
}
