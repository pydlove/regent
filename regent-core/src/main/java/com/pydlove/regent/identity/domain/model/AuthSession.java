package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 一次登录会话。只持有用户 ID，不持有另一个聚合根。 */
public class AuthSession {

    private AuthSessionId id;
    private final UserId userId;
    private final SessionTokenHash tokenHash;
    private Instant expiresAt;
    private final Instant createdAt;
    private final boolean remembered;

    private AuthSession(AuthSessionId id,
                        UserId userId,
                        SessionTokenHash tokenHash,
                        Instant expiresAt,
                        Instant createdAt,
                        boolean remembered) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "用户 ID");
        this.tokenHash = Objects.requireNonNull(tokenHash, "会话令牌哈希");
        this.expiresAt = Objects.requireNonNull(expiresAt, "会话到期时间");
        this.createdAt = Objects.requireNonNull(createdAt, "会话创建时间");
        this.remembered = remembered;
        if (!expiresAt.isAfter(createdAt)) {
            throw new DomainException("会话到期时间必须晚于创建时间");
        }
    }

    public static AuthSession open(UserId userId,
                                   SessionTokenHash tokenHash,
                                   Instant createdAt,
                                   Instant expiresAt,
                                   boolean remembered) {
        return new AuthSession(null, userId, tokenHash, expiresAt, createdAt, remembered);
    }

    public static AuthSession restore(AuthSessionId id,
                                      UserId userId,
                                      SessionTokenHash tokenHash,
                                      Instant expiresAt,
                                      Instant createdAt,
                                      boolean remembered) {
        if (id == null) {
            throw new DomainException("已持久化会话必须有 ID");
        }
        return new AuthSession(id, userId, tokenHash, expiresAt, createdAt, remembered);
    }

    /**
     * 保持登录的会话在有效期只剩一半时顺延。这样只要用户每隔一段时间打开一次平台，
     * 就不需要在正常使用过程中重新登录。
     */
    public boolean renewIfNeeded(Instant now, Duration ttl) {
        Objects.requireNonNull(now, "当前时间");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new DomainException("会话有效期必须大于零");
        }
        if (!remembered || !isActive(now)) {
            return false;
        }
        Instant renewBefore = now.plus(ttl.dividedBy(2));
        if (expiresAt.isAfter(renewBefore)) {
            return false;
        }
        expiresAt = now.plus(ttl);
        return true;
    }

    public boolean isActive(Instant now) {
        return expiresAt.isAfter(now);
    }

    public void assignIdentity(AuthSessionId assigned) {
        if (this.id != null) {
            throw new DomainException("会话已有 ID，不能重复分配");
        }
        this.id = Objects.requireNonNull(assigned, "会话 ID");
    }

    public AuthSessionId getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public SessionTokenHash getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRemembered() {
        return remembered;
    }
}
