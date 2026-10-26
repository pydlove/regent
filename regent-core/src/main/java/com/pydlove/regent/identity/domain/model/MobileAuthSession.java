package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * 手机安装实例的一组访问令牌与轮换令牌。
 *
 * <p>聚合只持有用户 ID，不持有用户对象。刷新令牌轮换时保留上一枚摘要，
 * 用于识别令牌重放并撤销整组会话。
 */
public class MobileAuthSession {

    private MobileAuthSessionId id;
    private final UserId userId;
    private final InstallationId installationId;
    private SessionTokenHash accessTokenHash;
    private SessionTokenHash refreshTokenHash;
    private SessionTokenHash previousRefreshTokenHash;
    private Instant accessExpiresAt;
    private Instant refreshExpiresAt;
    private final Instant createdAt;
    private Instant revokedAt;

    private MobileAuthSession(MobileAuthSessionId id,
                              UserId userId,
                              InstallationId installationId,
                              SessionTokenHash accessTokenHash,
                              SessionTokenHash refreshTokenHash,
                              SessionTokenHash previousRefreshTokenHash,
                              Instant accessExpiresAt,
                              Instant refreshExpiresAt,
                              Instant createdAt,
                              Instant revokedAt) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "用户 ID");
        this.installationId = Objects.requireNonNull(installationId, "手机安装标识");
        this.accessTokenHash = Objects.requireNonNull(accessTokenHash, "访问令牌摘要");
        this.refreshTokenHash = Objects.requireNonNull(refreshTokenHash, "刷新令牌摘要");
        this.previousRefreshTokenHash = previousRefreshTokenHash;
        this.accessExpiresAt = Objects.requireNonNull(accessExpiresAt, "访问令牌到期时间");
        this.refreshExpiresAt = Objects.requireNonNull(refreshExpiresAt, "刷新令牌到期时间");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间");
        this.revokedAt = revokedAt;
        if (!accessExpiresAt.isAfter(createdAt)) {
            throw new DomainException("访问令牌到期时间必须晚于创建时间");
        }
        if (!refreshExpiresAt.isAfter(accessExpiresAt)) {
            throw new DomainException("刷新令牌必须晚于访问令牌到期");
        }
    }

    public static MobileAuthSession open(UserId userId,
                                         InstallationId installationId,
                                         SessionTokenHash accessTokenHash,
                                         SessionTokenHash refreshTokenHash,
                                         Instant accessExpiresAt,
                                         Instant refreshExpiresAt,
                                         Instant createdAt) {
        return new MobileAuthSession(
                null,
                userId,
                installationId,
                accessTokenHash,
                refreshTokenHash,
                null,
                accessExpiresAt,
                refreshExpiresAt,
                createdAt,
                null);
    }

    public static MobileAuthSession restore(MobileAuthSessionId id,
                                            UserId userId,
                                            InstallationId installationId,
                                            SessionTokenHash accessTokenHash,
                                            SessionTokenHash refreshTokenHash,
                                            SessionTokenHash previousRefreshTokenHash,
                                            Instant accessExpiresAt,
                                            Instant refreshExpiresAt,
                                            Instant createdAt,
                                            Instant revokedAt) {
        if (id == null) {
            throw new DomainException("已持久化手机登录会话必须有 ID");
        }
        return new MobileAuthSession(
                id,
                userId,
                installationId,
                accessTokenHash,
                refreshTokenHash,
                previousRefreshTokenHash,
                accessExpiresAt,
                refreshExpiresAt,
                createdAt,
                revokedAt);
    }

    public boolean accessActiveAt(Instant now) {
        return revokedAt == null && accessExpiresAt.isAfter(now);
    }

    public boolean isPreviousRefreshToken(SessionTokenHash candidate) {
        return previousRefreshTokenHash != null && previousRefreshTokenHash.equals(candidate);
    }

    public boolean refreshActiveAt(Instant now) {
        return revokedAt == null && refreshExpiresAt.isAfter(now);
    }

    public void rotate(SessionTokenHash currentRefreshTokenHash,
                       SessionTokenHash nextAccessTokenHash,
                       SessionTokenHash nextRefreshTokenHash,
                       Instant now,
                       Instant nextAccessExpiresAt,
                       Instant nextRefreshExpiresAt) {
        Objects.requireNonNull(currentRefreshTokenHash, "当前刷新令牌摘要");
        Objects.requireNonNull(nextAccessTokenHash, "新访问令牌摘要");
        Objects.requireNonNull(nextRefreshTokenHash, "新刷新令牌摘要");
        Objects.requireNonNull(now, "轮换时间");
        if (!refreshActiveAt(now)) {
            throw new DomainException("刷新令牌已失效");
        }
        if (!refreshTokenHash.equals(currentRefreshTokenHash)) {
            throw new DomainException("刷新令牌摘要不匹配");
        }
        if (nextAccessExpiresAt == null || !nextAccessExpiresAt.isAfter(now)) {
            throw new DomainException("新访问令牌到期时间必须晚于当前时间");
        }
        if (nextRefreshExpiresAt == null || !nextRefreshExpiresAt.isAfter(now)) {
            throw new DomainException("新刷新令牌到期时间必须晚于当前时间");
        }
        if (!nextRefreshExpiresAt.isAfter(nextAccessExpiresAt)) {
            throw new DomainException("刷新令牌必须晚于访问令牌到期");
        }
        previousRefreshTokenHash = refreshTokenHash;
        accessTokenHash = nextAccessTokenHash;
        accessExpiresAt = nextAccessExpiresAt;
        refreshTokenHash = nextRefreshTokenHash;
        refreshExpiresAt = nextRefreshExpiresAt;
    }

    public void revoke(Instant now) {
        Objects.requireNonNull(now, "撤销时间");
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void assignIdentity(MobileAuthSessionId assigned) {
        if (this.id != null) {
            throw new DomainException("手机登录会话已有 ID");
        }
        this.id = Objects.requireNonNull(assigned, "手机登录会话 ID");
    }

    public MobileAuthSessionId getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public InstallationId getInstallationId() {
        return installationId;
    }

    public SessionTokenHash getAccessTokenHash() {
        return accessTokenHash;
    }

    public SessionTokenHash getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public SessionTokenHash getPreviousRefreshTokenHash() {
        return previousRefreshTokenHash;
    }

    public Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public Instant getRefreshExpiresAt() {
        return refreshExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
