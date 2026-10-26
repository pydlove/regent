package com.pydlove.regent.identity.application.command;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.application.query.MobileAuthGrant;
import com.pydlove.regent.identity.domain.model.InstallationId;
import com.pydlove.regent.identity.domain.model.MobileAuthSession;
import com.pydlove.regent.identity.domain.model.MobileAuthSessionId;
import com.pydlove.regent.identity.domain.model.SessionTokenHash;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.port.SessionTokenCodec;
import com.pydlove.regent.identity.domain.repository.MobileAuthSessionRepository;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.shared.domain.AuthenticationException;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 手机 App 的独立 Bearer 会话，不复用浏览器 Cookie。 */
public class MobileAuthService {

    static final String ACCESS_PREFIX = "mat_";
    static final String REFRESH_PREFIX = "mrt_";
    private static final int MAX_TOKEN_LENGTH = 128;

    private final CredentialAuthenticator credentialAuthenticator;
    private final UserRepository userRepository;
    private final MobileAuthSessionRepository sessionRepository;
    private final SessionTokenCodec tokenCodec;
    private final TransactionRunner transactionRunner;
    private final Clock clock;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public MobileAuthService(CredentialAuthenticator credentialAuthenticator,
                             UserRepository userRepository,
                             MobileAuthSessionRepository sessionRepository,
                             SessionTokenCodec tokenCodec,
                             TransactionRunner transactionRunner,
                             Clock clock,
                             Duration accessTtl,
                             Duration refreshTtl) {
        this.credentialAuthenticator = Objects.requireNonNull(
                credentialAuthenticator, "账号凭据认证器");
        this.userRepository = Objects.requireNonNull(userRepository, "用户仓储");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "手机登录会话仓储");
        this.tokenCodec = Objects.requireNonNull(tokenCodec, "会话令牌编解码器");
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "事务执行器");
        this.clock = Objects.requireNonNull(clock, "时钟");
        this.accessTtl = requirePositive(accessTtl, "手机访问令牌有效期");
        this.refreshTtl = requirePositive(refreshTtl, "手机刷新令牌有效期");
        if (refreshTtl.compareTo(accessTtl) <= 0) {
            throw new DomainException("手机刷新令牌有效期必须长于访问令牌");
        }
    }

    public MobileAuthGrant login(LoginCommand command, String installationId) {
        AuthenticatedUser user = credentialAuthenticator.authenticateCredentials(command);
        InstallationId installation = InstallationId.of(installationId);
        return transactionRunner.inTransaction(() -> {
            Instant now = clock.instant();
            sessionRepository.revokeActiveByInstallation(
                    UserId.of(user.id()), installation, now);
            return issue(user, installation, now);
        });
    }

    public Optional<AuthenticatedUser> authenticate(String rawAccessToken) {
        if (!hasPrefix(rawAccessToken, ACCESS_PREFIX)) {
            return Optional.empty();
        }
        SessionTokenHash hash;
        try {
            hash = tokenCodec.hash(rawAccessToken);
        } catch (DomainException ignored) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessionRepository.findActiveByAccessToken(hash, now)
                .flatMap(session -> userRepository.findById(session.getUserId()))
                .map(AuthenticatedUser::from);
    }

    public MobileAuthGrant refresh(String rawRefreshToken) {
        if (!hasPrefix(rawRefreshToken, REFRESH_PREFIX)) {
            throw invalidSession();
        }
        SessionTokenHash incomingHash;
        try {
            incomingHash = tokenCodec.hash(rawRefreshToken);
        } catch (DomainException ignored) {
            throw invalidSession();
        }
        RefreshResult result = transactionRunner.inTransaction(() -> {
            MobileAuthSession session = sessionRepository.findByRefreshToken(incomingHash)
                    .orElse(null);
            Instant now = clock.instant();
            if (session == null) {
                MobileAuthSessionId replayedSessionId = sessionRepository
                        .findSessionIdByUsedRefreshToken(incomingHash)
                        .orElse(null);
                if (replayedSessionId == null) {
                    throw invalidSession();
                }
                sessionRepository.findByIdForUpdate(replayedSessionId).ifPresent(replayed -> {
                    replayed.revoke(now);
                    sessionRepository.update(replayed);
                });
                return new RefreshResult(null, true);
            }
            if (session.isPreviousRefreshToken(incomingHash)) {
                session.revoke(now);
                sessionRepository.update(session);
                return new RefreshResult(null, true);
            }
            if (!session.refreshActiveAt(now)) {
                throw invalidSession();
            }
            AuthenticatedUser user = userRepository.findById(session.getUserId())
                    .map(AuthenticatedUser::from)
                    .orElseThrow(MobileAuthService::invalidSession);
            String accessRaw = ACCESS_PREFIX + tokenCodec.generateRawToken();
            String refreshRaw = REFRESH_PREFIX + tokenCodec.generateRawToken();
            Instant nextAccessExpiresAt = now.plus(accessTtl);
            Instant nextRefreshExpiresAt = now.plus(refreshTtl);
            sessionRepository.recordRefreshTokenUse(session.getId(), incomingHash, now);
            session.rotate(
                    incomingHash,
                    tokenCodec.hash(accessRaw),
                    tokenCodec.hash(refreshRaw),
                    now,
                    nextAccessExpiresAt,
                    nextRefreshExpiresAt);
            sessionRepository.update(session);
            return new RefreshResult(new MobileAuthGrant(
                    user,
                    accessRaw,
                    nextAccessExpiresAt,
                    refreshRaw,
                    nextRefreshExpiresAt), false);
        });
        if (result.replay()) {
            throw invalidSession();
        }
        return result.grant();
    }

    public void logout(String rawAccessToken) {
        if (!hasPrefix(rawAccessToken, ACCESS_PREFIX)) {
            return;
        }
        SessionTokenHash hash;
        try {
            hash = tokenCodec.hash(rawAccessToken);
        } catch (DomainException ignored) {
            return;
        }
        transactionRunner.inTransaction(() -> {
            Instant now = clock.instant();
            sessionRepository.findActiveByAccessToken(hash, now).ifPresent(session -> {
                session.revoke(now);
                sessionRepository.update(session);
            });
        });
    }

    private MobileAuthGrant issue(AuthenticatedUser user,
                                  InstallationId installation,
                                  Instant now) {
        String accessRaw = ACCESS_PREFIX + tokenCodec.generateRawToken();
        String refreshRaw = REFRESH_PREFIX + tokenCodec.generateRawToken();
        Instant accessExpiresAt = now.plus(accessTtl);
        Instant refreshExpiresAt = now.plus(refreshTtl);
        MobileAuthSession session = MobileAuthSession.open(
                UserId.of(user.id()),
                installation,
                tokenCodec.hash(accessRaw),
                tokenCodec.hash(refreshRaw),
                accessExpiresAt,
                refreshExpiresAt,
                now);
        sessionRepository.save(session);
        return new MobileAuthGrant(
                user, accessRaw, accessExpiresAt, refreshRaw, refreshExpiresAt);
    }

    private static boolean hasPrefix(String token, String prefix) {
        return token != null
                && token.length() <= MAX_TOKEN_LENGTH
                && token.startsWith(prefix);
    }

    private static Duration requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new DomainException(label + "必须大于零");
        }
        return value;
    }

    private static AuthenticationException invalidSession() {
        return new AuthenticationException("登录已失效，请重新登录");
    }

    private record RefreshResult(MobileAuthGrant grant, boolean replay) {
    }
}
