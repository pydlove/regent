package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobileAuthSessionTest {

    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    private static final SessionTokenHash ACCESS = hash('a');
    private static final SessionTokenHash REFRESH = hash('b');
    private static final SessionTokenHash NEXT_ACCESS = hash('c');
    private static final SessionTokenHash NEXT_REFRESH = hash('d');

    @Test
    @DisplayName("访问令牌按自己的到期时间失效")
    void accessTokenExpiresIndependently() {
        MobileAuthSession session = session();

        assertTrue(session.accessActiveAt(NOW));
        assertFalse(session.accessActiveAt(NOW.plus(Duration.ofMinutes(31))));
        assertTrue(session.refreshActiveAt(NOW.plus(Duration.ofDays(29))));
    }

    @Test
    @DisplayName("刷新令牌轮换会替换访问令牌并保留上一枚刷新摘要")
    void rotatesTokenPair() {
        MobileAuthSession session = session();
        Instant rotatedAt = NOW.plus(Duration.ofMinutes(20));
        Instant accessExpiresAt = rotatedAt.plus(Duration.ofMinutes(30));
        Instant refreshExpiresAt = rotatedAt.plus(Duration.ofDays(30));

        session.rotate(
                REFRESH, NEXT_ACCESS, NEXT_REFRESH, rotatedAt, accessExpiresAt, refreshExpiresAt);

        assertEquals(NEXT_ACCESS, session.getAccessTokenHash());
        assertEquals(NEXT_REFRESH, session.getRefreshTokenHash());
        assertEquals(REFRESH, session.getPreviousRefreshTokenHash());
        assertEquals(accessExpiresAt, session.getAccessExpiresAt());
        assertEquals(refreshExpiresAt, session.getRefreshExpiresAt());
        assertTrue(session.isPreviousRefreshToken(REFRESH));
    }

    @Test
    @DisplayName("过期或已撤销的会话不能继续轮换")
    void rejectsInactiveRotation() {
        MobileAuthSession expired = session();
        Instant afterRefreshExpiry = NOW.plus(Duration.ofDays(31));

        assertThrows(DomainException.class, () -> expired.rotate(
                REFRESH,
                NEXT_ACCESS,
                NEXT_REFRESH,
                afterRefreshExpiry,
                afterRefreshExpiry.plus(Duration.ofMinutes(30)),
                afterRefreshExpiry.plus(Duration.ofDays(30))));

        MobileAuthSession revoked = session();
        revoked.revoke(NOW.plusSeconds(1));
        assertFalse(revoked.refreshActiveAt(NOW.plusSeconds(2)));
    }

    private static MobileAuthSession session() {
        return MobileAuthSession.open(
                UserId.of(7),
                InstallationId.of("install-12345678"),
                ACCESS,
                REFRESH,
                NOW.plus(Duration.ofMinutes(30)),
                NOW.plus(Duration.ofDays(30)),
                NOW);
    }

    private static SessionTokenHash hash(char value) {
        return new SessionTokenHash(String.valueOf(value).repeat(64));
    }
}
