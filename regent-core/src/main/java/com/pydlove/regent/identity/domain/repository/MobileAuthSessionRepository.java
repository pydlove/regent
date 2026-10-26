package com.pydlove.regent.identity.domain.repository;

import com.pydlove.regent.identity.domain.model.InstallationId;
import com.pydlove.regent.identity.domain.model.MobileAuthSession;
import com.pydlove.regent.identity.domain.model.MobileAuthSessionId;
import com.pydlove.regent.identity.domain.model.SessionTokenHash;
import com.pydlove.regent.identity.domain.model.UserId;

import java.time.Instant;
import java.util.Optional;

public interface MobileAuthSessionRepository {

    void save(MobileAuthSession session);

    void update(MobileAuthSession session);

    Optional<MobileAuthSession> findActiveByAccessToken(SessionTokenHash tokenHash, Instant now);

    Optional<MobileAuthSession> findByRefreshToken(SessionTokenHash tokenHash);

    Optional<MobileAuthSession> findByIdForUpdate(MobileAuthSessionId id);

    Optional<MobileAuthSessionId> findSessionIdByUsedRefreshToken(SessionTokenHash tokenHash);

    void recordRefreshTokenUse(MobileAuthSessionId sessionId,
                               SessionTokenHash tokenHash,
                               Instant usedAt);

    void revokeActiveByInstallation(UserId userId, InstallationId installationId, Instant now);
}
