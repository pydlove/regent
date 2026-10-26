package com.pydlove.regent.identity.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.InstallationId;
import com.pydlove.regent.identity.domain.model.MobileAuthSession;
import com.pydlove.regent.identity.domain.model.MobileAuthSessionId;
import com.pydlove.regent.identity.domain.model.SessionTokenHash;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.repository.MobileAuthSessionRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class MyBatisMobileAuthSessionRepository implements MobileAuthSessionRepository {

    private final MobileAuthSessionMapper mapper;

    public MyBatisMobileAuthSessionRepository(MobileAuthSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(MobileAuthSession session) {
        MobileAuthSessionData data = toData(session);
        mapper.insert(data);
        session.assignIdentity(MobileAuthSessionId.of(data.getId()));
    }

    @Override
    public void update(MobileAuthSession session) {
        if (session.getId() == null) {
            throw new DomainException("未持久化的手机登录会话不能更新");
        }
        mapper.update(toData(session));
    }

    @Override
    public Optional<MobileAuthSession> findActiveByAccessToken(SessionTokenHash tokenHash,
                                                               Instant now) {
        return Optional.ofNullable(mapper.findActiveByAccessToken(
                        tokenHash.value(),
                        now.atZone(ZoneOffset.UTC).toLocalDateTime()))
                .map(this::toDomain);
    }

    @Override
    public Optional<MobileAuthSession> findByRefreshToken(SessionTokenHash tokenHash) {
        return Optional.ofNullable(mapper.findByRefreshTokenForUpdate(tokenHash.value()))
                .map(this::toDomain);
    }

    @Override
    public Optional<MobileAuthSession> findByIdForUpdate(MobileAuthSessionId id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id.value()))
                .map(this::toDomain);
    }

    @Override
    public Optional<MobileAuthSessionId> findSessionIdByUsedRefreshToken(
            SessionTokenHash tokenHash) {
        return Optional.ofNullable(mapper.findSessionIdByUsedRefreshToken(tokenHash.value()))
                .map(MobileAuthSessionId::of);
    }

    @Override
    public void recordRefreshTokenUse(MobileAuthSessionId sessionId,
                                      SessionTokenHash tokenHash,
                                      Instant usedAt) {
        mapper.insertRefreshTokenUse(
                sessionId.value(),
                tokenHash.value(),
                usedAt.atZone(ZoneOffset.UTC).toLocalDateTime());
    }

    @Override
    public void revokeActiveByInstallation(UserId userId,
                                           InstallationId installationId,
                                           Instant now) {
        mapper.revokeActiveByInstallation(
                userId.value(),
                installationId.value(),
                now.atZone(ZoneOffset.UTC).toLocalDateTime());
    }

    private MobileAuthSessionData toData(MobileAuthSession session) {
        MobileAuthSessionData data = new MobileAuthSessionData();
        data.setId(session.getId() == null ? null : session.getId().value());
        data.setUserId(session.getUserId().value());
        data.setInstallationId(session.getInstallationId().value());
        data.setAccessTokenHash(session.getAccessTokenHash().value());
        data.setRefreshTokenHash(session.getRefreshTokenHash().value());
        data.setPreviousRefreshTokenHash(session.getPreviousRefreshTokenHash() == null
                ? null
                : session.getPreviousRefreshTokenHash().value());
        data.setAccessExpiresAt(localDateTime(session.getAccessExpiresAt()));
        data.setRefreshExpiresAt(localDateTime(session.getRefreshExpiresAt()));
        data.setCreatedAt(localDateTime(session.getCreatedAt()));
        data.setRevokedAt(session.getRevokedAt() == null
                ? null
                : localDateTime(session.getRevokedAt()));
        return data;
    }

    private MobileAuthSession toDomain(MobileAuthSessionData data) {
        return MobileAuthSession.restore(
                MobileAuthSessionId.of(data.getId()),
                UserId.of(data.getUserId()),
                InstallationId.of(data.getInstallationId()),
                new SessionTokenHash(data.getAccessTokenHash()),
                new SessionTokenHash(data.getRefreshTokenHash()),
                data.getPreviousRefreshTokenHash() == null
                        ? null
                        : new SessionTokenHash(data.getPreviousRefreshTokenHash()),
                instant(data.getAccessExpiresAt()),
                instant(data.getRefreshExpiresAt()),
                instant(data.getCreatedAt()),
                data.getRevokedAt() == null ? null : instant(data.getRevokedAt()));
    }

    private static java.time.LocalDateTime localDateTime(Instant value) {
        return value.atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    private static Instant instant(java.time.LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }
}
